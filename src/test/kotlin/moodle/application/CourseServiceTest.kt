package moodle.application

import moodle.cli.CourseCommands
import moodle.cli.i18n.Messages
import moodle.domain.AccessToken
import moodle.domain.PublicStatus
import moodle.domain.SiteInfo
import moodle.domain.entities.Course
import moodle.domain.entities.CourseFile
import moodle.domain.entities.CourseModule
import moodle.domain.entities.CourseSearchHit
import moodle.domain.entities.CourseSection
import moodle.domain.entities.DownloadedFile
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.CourseFiles
import moodle.domain.interfaces.CourseRepository
import moodle.domain.interfaces.MoodleAccess
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CourseServiceTest {
    private val token = AccessToken("a".repeat(32))
    private val site = "https://moodle.example"
    private val access = AccessService(
        object : MoodleAccess {
            override fun getPublicStatus(): PublicStatus = error("Not needed")

            override fun getSiteInfo(token: AccessToken): SiteInfo = SiteInfo(site, "Moodle", 42, "Student", emptySet())
        },
        site
    )
    private val repository = FakeCourses()
    private val files = FakeFiles()
    private val service = CourseService(access, repository, files)

    @Test
    fun `login is required before accessing courses and logout removes access`() {
        assertFailsWith<MoodleException.Authentication> { service.getCourses() }
        assertFalse(repository.called)
        access.login(token)
        assertEquals("Programming", service.getCourses().single().name)
        assertEquals(token, repository.receivedToken)
        assertEquals(42L, repository.receivedUser)
        access.logout()
        assertFailsWith<MoodleException.Authentication> { service.getContents(12) }
    }

    @Test
    fun `course commands display text file selectors and download the selected course resource`() {
        access.login(token)
        val output = mutableListOf<String>()
        val commands = CourseCommands(service, output::add)
        commands.run(listOf("courses"))
        commands.run(listOf("course", "12"))
        assertTrue(output.any { it.contains("12  Programming") })
        assertTrue(output.any { it.contains("Overview & introduction") })
        assertTrue(output.any { it.contains("download 12 34 1") })
        assertFalse(output.any { it.contains("<p>") || it.contains(token.value) })
        commands.run(listOf("read", "12", "34"))
        assertTrue(output.last().contains("Full page text"))
        commands.run(listOf("download", "12", "34", "1", "/tmp/My Files"))
        assertEquals("/tmp/My Files", files.directory)
        assertEquals("index.html", files.selected?.name)
        assertEquals(token, files.receivedToken)
    }

    @Test
    fun `invalid selections fail before downloading and authentication failures clear session`() {
        access.login(token)
        assertFailsWith<MoodleException.Input> { service.getContents(-1) }
        assertFailsWith<MoodleException.Input> { service.download(12, 999, 1, "downloads") }
        assertFailsWith<MoodleException.Input> { service.download(12, 34, 2, "downloads") }
        assertEquals(null, files.selected)
        repository.reject = true
        assertFailsWith<MoodleException.Authentication> { service.getCourses() }
        repository.reject = false
        assertFailsWith<MoodleException.Authentication> { service.getCourses() }
    }

    @Test
    fun `search finds course titles activities and files across courses with the original download selector`() {
        access.login(token)
        repository.courses = listOf(Course(21, "Mathematics"), Course(12, "Recursion lab"), Course(32, "Restricted"))
        repository.restrictedCourse = 32

        val result = service.search("rEcUrSiOn")
        assertEquals(1, result.skippedCourses)
        assertEquals(CourseSearchHit.CourseTitle(Course(12, "Recursion lab")), result.items[0])
        val activity = result.items.filterIsInstance<CourseSearchHit.Activity>().single()
        assertEquals(21L, activity.course.id)
        val file = result.items.filterIsInstance<CourseSearchHit.File>().single()
        assertEquals(activity, file.activity)
        assertEquals(2, file.number)

        val output = mutableListOf<String>()
        val cli = CourseCommands(service, output::add, Messages(Locale.ENGLISH))
        assertEquals(0, cli.run(listOf("search", "RECURSION", "slides")))
        assertTrue(output.any { it.contains("incomplete") })
        assertTrue(output.any { it.contains("Recursion_Slides.pdf") && it.contains("Mathematics") })
        assertTrue(output.any { it.trim() == "download 21 56 2" })
        assertEquals(null, files.selected)
    }

    @Test
    fun `invalid search is rejected before listing courses`() {
        access.login(token)
        assertFailsWith<MoodleException.Input> { service.search(" - _ ") }
        assertFailsWith<MoodleException.Input> { service.search("x".repeat(121)) }
        assertFalse(repository.called)
    }

    private class FakeCourses : CourseRepository {
        var called = false
        var reject = false
        var courses = listOf(Course(12, "Programming"))
        var restrictedCourse: Long? = null
        var receivedToken: AccessToken? = null
        var receivedUser: Long? = null

        override fun getCourses(token: AccessToken, userId: Long): List<Course> {
            called = true
            receivedToken = token
            receivedUser = userId
            if (reject) throw MoodleException.Authentication("Expired")
            return courses
        }

        override fun getContents(token: AccessToken, courseId: Long): List<CourseSection> {
            if (courseId == restrictedCourse) throw MoodleException.Permission("error.api.courseAccess")
            if (courseId == 21L) {
                val module = CourseModule(
                    56,
                    "Recursion examples",
                    "resource",
                    "",
                    emptyList(),
                    listOf(
                        CourseFile("Outline.pdf", "https://moodle.example/outline", 0, null),
                        CourseFile("Recursion_Slides.pdf", "https://moodle.example/slides", 0, null)
                    )
                )
                return listOf(CourseSection(2, "Week 2", "", listOf(module)))
            }
            return programmingContents()
        }

        private fun programmingContents(): List<CourseSection> = listOf(
            CourseSection(
                1,
                "Week 1",
                "<p>Overview &amp; introduction</p>",
                listOf(
                    CourseModule(
                        34,
                        "Instructions",
                        "page",
                        "<p>Short intro</p>",
                        emptyList(),
                        listOf(
                            CourseFile(
                                "index.html",
                                "https://moodle.example/webservice/pluginfile.php/1/index.html",
                                0,
                                "text/html"
                            )
                        )
                    )
                )
            )
        )
    }

    private class FakeFiles : CourseFiles {
        var selected: CourseFile? = null
        var directory: String? = null
        var receivedToken: AccessToken? = null

        override fun download(token: AccessToken, file: CourseFile, directory: String): DownloadedFile {
            selected = file
            this.directory = directory
            receivedToken = token
            return DownloadedFile("$directory/${file.name}", 100)
        }

        override fun readText(token: AccessToken, file: CourseFile): String = "<p>Full page text</p>"
    }
}
