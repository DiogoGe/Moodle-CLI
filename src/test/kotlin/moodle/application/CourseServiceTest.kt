package moodle.application

import moodle.cli.CourseCommands
import moodle.cli.i18n.Messages
import moodle.config.MoodleConfig
import moodle.domain.AccessToken
import moodle.domain.PublicStatus
import moodle.domain.SiteInfo
import moodle.domain.entities.Course
import moodle.domain.entities.CourseDownloadOutcome
import moodle.domain.entities.CourseFile
import moodle.domain.entities.CourseFileSelection
import moodle.domain.entities.CourseModule
import moodle.domain.entities.CourseSearchHit
import moodle.domain.entities.CourseSection
import moodle.domain.entities.DownloadedFile
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.CourseFiles
import moodle.domain.interfaces.CourseRepository
import moodle.domain.interfaces.MoodleAccess
import moodle.presentation.PlainText
import java.net.URI
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
    private val service = CourseService(access, repository, files, PlainText()::render)

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
        assertTrue(output.any { it.contains("Generics overview & introduction") })
        assertTrue(output.any { it.contains("download 12 34 1") })
        assertTrue(output.any { it.trim().endsWith("open 12 34") })
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
        assertTrue(output.any { it.trim().endsWith("open 21 56") })
        assertEquals(null, files.selected)
    }

    @Test
    fun `invalid search is rejected before listing courses`() {
        access.login(token)
        assertFailsWith<MoodleException.Input> { service.search(" - _ ") }
        assertFailsWith<MoodleException.Input> { service.search("x".repeat(121)) }
        assertFalse(repository.called)
    }

    @Test
    fun `search finds visible descriptions and inline text without reading Page files`() {
        access.login(token)
        repository.courses = listOf(Course(12, "Programming", "<p>Generics &amp; variance</p>"), Course(21, "Other"))
        val result = service.search("GEN", courseId = 12)

        val course = result.items.filterIsInstance<CourseSearchHit.CourseTitle>().single()
        val section = result.items.filterIsInstance<CourseSearchHit.SectionText>().single()
        val module = result.items.filterIsInstance<CourseSearchHit.Activity>().single()
        assertEquals("Generics & variance", course.snippet)
        assertEquals("Generics overview & introduction", section.snippet)
        assertEquals("Practice generics", module.snippet)
        assertTrue(service.search("hiddenkeyword", courseId = 12).items.isEmpty())
        assertTrue(service.search("short intro", courseId = 12).items.single() is CourseSearchHit.Activity)
        assertTrue(files.reads.isEmpty())
        assertEquals(listOf(12L, 12L, 12L), repository.contentCalls)
    }

    @Test
    fun `Page search limits reads and can continue after unreadable files`() {
        access.login(token)
        repository.courses = (1L..12L).map { Course(it, "Course $it") }
        files.pageText = "<p>${"😀".repeat(140)} unique phrase &amp; context</p>"
        files.firstPageError = MoodleException.Download("error.download.denied")
        val first = service.search("unique phrase", pageOffset = 0)
        assertEquals(10, files.reads.size)
        assertEquals(9, first.items.filterIsInstance<CourseSearchHit.PageText>().size)
        assertEquals(10, first.nextPageOffset)
        assertEquals(1, first.skippedPages)

        val second = service.search("unique phrase", pageOffset = first.nextPageOffset)
        assertEquals(12, files.reads.size)
        assertEquals(null, second.nextPageOffset)
        assertEquals(listOf(11L, 12L), second.items.filterIsInstance<CourseSearchHit.PageText>().map { it.activity.course.id })
        val snippet = second.items
            .filterIsInstance<CourseSearchHit.PageText>()
            .first()
            .snippet
        assertTrue(snippet.startsWith("…") && snippet.contains("unique phrase & context"))
    }

    @Test
    fun `Page search propagates expired authentication instead of returning an empty result`() {
        access.login(token)
        files.firstPageError = MoodleException.Authentication("error.session.expired")
        assertFailsWith<MoodleException.Authentication> { service.search("intro", pageOffset = 0) }
        assertFailsWith<MoodleException.Authentication> { service.getCourses() }
    }

    @Test
    fun `open sends the verified activity to the browser on the configured site`() {
        access.login(token)
        val visited = mutableListOf<URI>()
        val commands = CourseCommands(
            service,
            output = {},
            config = MoodleConfig("https://moodle.example/campus/"),
            browse = visited::add
        )

        assertEquals(0, commands.run(listOf("open", "12", "34")))
        assertEquals(listOf(URI("https://moodle.example/campus/mod/page/view.php?id=34&forceview=1")), visited)
        assertFailsWith<MoodleException.Input> { commands.run(listOf("open", "12", "56")) }
        assertFailsWith<MoodleException.Input> { commands.run(listOf("open", "12", "-1")) }
        assertEquals(1, visited.size)
        assertEquals(listOf(12L, 12L), repository.contentCalls)
    }

    @Test
    fun `open prints a copyable course link when the activity has no page and the browser cannot launch`() {
        access.login(token)
        repository.noViewLink = true
        val output = mutableListOf<String>()
        val commands = CourseCommands(service, output::add, Messages(Locale.ENGLISH), MoodleConfig("$site/"), browse = { false })

        assertEquals(1, commands.run(listOf("open", "12", "34")))
        assertTrue(output.single().endsWith("$site/course/view.php?id=12#module-34"))
    }

    @Test
    fun `open keeps an unexpected module type on the configured Moodle site`() {
        access.login(token)
        repository.moduleType = "../../external?token=fixture"
        val visited = mutableListOf<URI>()
        val commands = CourseCommands(service, output = {}, config = MoodleConfig("$site/"), browse = visited::add)

        assertEquals(0, commands.run(listOf("open", "12", "34")))
        assertEquals(URI("$site/course/view.php?id=12#module-34"), visited.single())
    }

    @Test
    fun `batch preserves selected order and continues after a file conflict with one metadata request per course`() {
        access.login(token)
        files.failures["Outline.pdf"] = MoodleException.Download("error.download.exists")
        val outline = CourseFileSelection(21, 56, 1)
        val slides = CourseFileSelection(21, 56, 2)
        val page = CourseFileSelection(12, 34, 1)

        val outcomes = service.downloadMany(listOf(outline, page, slides, slides), "materials")

        assertEquals(listOf(outline, page, slides), outcomes.map { it.selection })
        assertEquals(listOf(21L, 12L), repository.contentCalls)
        assertEquals(listOf("Outline.pdf", "index.html", "Recursion_Slides.pdf"), files.attempts)
        assertEquals("error.download.exists", (outcomes[0] as CourseDownloadOutcome.Failed).error.messageKey)
        assertEquals("materials/index.html", (outcomes[1] as CourseDownloadOutcome.Saved).file.path)
        assertEquals("materials/Recursion_Slides.pdf", (outcomes[2] as CourseDownloadOutcome.Saved).file.path)
    }

    @Test
    fun `batch keeps completed files and stops remaining downloads when authentication expires`() {
        access.login(token)
        files.failures["Outline.pdf"] = MoodleException.Authentication("error.session.expired")
        val page = CourseFileSelection(12, 34, 1)
        val outline = CourseFileSelection(21, 56, 1)
        val slides = CourseFileSelection(21, 56, 2)

        val outcomes = service.downloadMany(listOf(page, outline, slides), "materials")

        assertTrue(outcomes[0] is CourseDownloadOutcome.Saved)
        assertTrue((outcomes[1] as CourseDownloadOutcome.Failed).error is MoodleException.Authentication)
        assertEquals(CourseDownloadOutcome.Skipped(slides), outcomes[2])
        assertEquals(listOf("index.html", "Outline.pdf"), files.attempts)
        assertFailsWith<MoodleException.Authentication> { service.getCourses() }
    }

    @Test
    fun `authentication expiring on the first download still reports the remaining files as skipped`() {
        access.login(token)
        val outline = CourseFileSelection(21, 56, 1)
        val slides = CourseFileSelection(21, 56, 2)
        val error = MoodleException.Authentication("error.session.expired")
        files.failures["Outline.pdf"] = error

        val outcomes = service.downloadMany(listOf(outline, slides), "materials")

        assertEquals(listOf(CourseDownloadOutcome.Failed(outline, error), CourseDownloadOutcome.Skipped(slides)), outcomes)
        assertEquals(listOf("Outline.pdf"), files.attempts)
    }

    @Test
    fun `CLI reports a failed batch item and saves later selections to the requested directory`() {
        access.login(token)
        val output = mutableListOf<String>()
        val commands = CourseCommands(service, output::add, Messages(Locale.ENGLISH))
        assertEquals(1, commands.run(listOf("download-many", "21:56:3", "21:56:2", "--directory", "My Materials")))
        assertTrue(output.first().contains("[21:56:3] Failed"))
        assertTrue(output.last().contains("My Materials/Recursion_Slides.pdf"))
        assertEquals(listOf(21L), repository.contentCalls)
    }

    @Test
    fun `CLI organizes selected files by course and activity under the requested directory`() {
        access.login(token)
        val commands = CourseCommands(service, output = {})
        val args = listOf("download-many", "21:56:1", "12:34:1", "--directory", "My Materials", "--organize")

        assertEquals(0, commands.run(args))
        assertEquals(
            listOf("My Materials/course-21/activity-56/Outline.pdf", "My Materials/course-12/activity-34/index.html"),
            files.savedPaths
        )
        assertEquals(listOf(21L, 12L), repository.contentCalls)
    }

    private class FakeCourses : CourseRepository {
        var called = false
        var reject = false
        var courses = listOf(Course(12, "Programming"))
        var restrictedCourse: Long? = null
        var noViewLink = false
        var moduleType = "page"
        val contentCalls = mutableListOf<Long>()
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
            contentCalls += courseId
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
                "<p>Generics overview &amp; introduction</p>",
                listOf(
                    CourseModule(
                        34,
                        "Instructions",
                        moduleType,
                        "<p>Short intro</p><script>hiddenkeyword</script>",
                        listOf("<p>Practice generics</p>"),
                        listOf(
                            CourseFile(
                                "index.html",
                                "https://moodle.example/webservice/pluginfile.php/1/index.html",
                                0,
                                "text/html"
                            )
                        ),
                        noViewLink = noViewLink
                    )
                )
            ),
            CourseSection(3, "Hidden section", "<p>hiddenkeyword</p>", emptyList(), userVisible = false)
        )
    }

    private class FakeFiles : CourseFiles {
        var selected: CourseFile? = null
        var directory: String? = null
        var receivedToken: AccessToken? = null
        val attempts = mutableListOf<String>()
        val failures = mutableMapOf<String, MoodleException>()
        val savedPaths = mutableListOf<String>()
        val reads = mutableListOf<CourseFile>()
        var pageText = "<p>Full page text</p>"
        var firstPageError: MoodleException? = null

        override fun download(
            token: AccessToken,
            file: CourseFile,
            directory: String,
            subdirectories: List<String>
        ): DownloadedFile {
            attempts += file.name
            failures[file.name]?.let { throw it }
            selected = file
            this.directory = directory
            receivedToken = token
            val path = (listOf(directory) + subdirectories + file.name).joinToString("/")
            savedPaths += path
            return DownloadedFile(path, 100)
        }

        override fun readText(token: AccessToken, file: CourseFile): String {
            reads += file
            if (reads.size == 1) firstPageError?.let { throw it }
            return pageText
        }
    }
}
