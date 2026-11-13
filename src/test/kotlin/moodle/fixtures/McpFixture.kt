package moodle.fixtures

import moodle.MoodleApplication
import moodle.application.AccessService
import moodle.application.AssignmentService
import moodle.application.CourseService
import moodle.application.QuizService
import moodle.cli.i18n.Messages
import moodle.domain.AccessToken
import moodle.domain.PublicStatus
import moodle.domain.SiteInfo
import moodle.domain.entities.Assignment
import moodle.domain.entities.AssignmentList
import moodle.domain.entities.Course
import moodle.domain.entities.CourseFile
import moodle.domain.entities.CourseModule
import moodle.domain.entities.CourseSection
import moodle.domain.entities.DownloadedFile
import moodle.domain.entities.Quiz
import moodle.domain.entities.QuizList
import moodle.domain.entities.QuizTiming
import moodle.domain.interfaces.CourseFiles
import moodle.domain.interfaces.CourseRepository
import moodle.domain.interfaces.MoodleAccess
import moodle.mcp.MoodleMcpTools
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

internal class McpFixture {
    val token = "a".repeat(32)
    val clock: Clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC)
    var courses = List(30) { Course(it + 1L, "<b>Course ${it + 1}</b>") }
    var text = "<p>First paragraph</p><script>private script</script><p>Last paragraph</p>"
    var failure: RuntimeException? = null
    private val courseCallCount = java.util.concurrent.atomic
        .AtomicInteger()
    val courseCalls: Int get() = courseCallCount.get()
    var beforeCourses: () -> Unit = {}
    var beforeDownload: () -> Unit = {}
    var downloadedFile: String? = null
    val assignments = FakeAssignmentRepository(AssignmentList(listOf(Assignment(10, 1, "Task", clock.instant())), partial = true))
    val quizzes = FakeQuizRepository(QuizList(listOf(Quiz(20, 1, "Quiz", QuizTiming(null, null, Duration.ofMinutes(5))))))
    val access = AccessService(
        object : MoodleAccess {
            override fun getPublicStatus(): PublicStatus =
                PublicStatus("https://fixture.invalid", "Fixture", true, true, true, false)
            override fun getSiteInfo(token: AccessToken): SiteInfo =
                SiteInfo("https://fixture.invalid", "Fixture", 42, "Private student name", emptySet())
        },
        "https://fixture.invalid"
    )
    private val repository = object : CourseRepository {
        override fun getCourses(token: AccessToken, userId: Long): List<Course> {
            courseCallCount.incrementAndGet()
            beforeCourses()
            failure?.let { throw it }
            return courses
        }

        override fun getContents(token: AccessToken, courseId: Long): List<CourseSection> {
            courseCallCount.incrementAndGet()
            val file =
                CourseFile(
                    "notes.pdf",
                    "https://fixture.invalid/webservice/pluginfile.php/1?token=${this@McpFixture.token}",
                    123,
                    null
                )
            val module =
                CourseModule(
                    5,
                    "<b>Notes</b>",
                    "page",
                    "<p>Description</p>",
                    emptyList(),
                    listOf(file, file.copy(name = "index.html"))
                )
            return listOf(CourseSection(3, "Section", "Section text", listOf(module)))
        }
    }
    private val files = object : CourseFiles {
        override fun download(
            token: AccessToken,
            file: CourseFile,
            directory: String,
            subdirectories: List<String>
        ): DownloadedFile {
            beforeDownload()
            downloadedFile = file.name
            return DownloadedFile(
                java.nio.file.Path
                    .of(directory, *(subdirectories + file.name).toTypedArray())
                    .toString(),
                file.size
            )
        }
        override fun readText(token: AccessToken, file: CourseFile): String = text
    }
    val application = MoodleApplication(
        access,
        CourseService(access, repository, files),
        AssignmentService(access, assignments, clock),
        QuizService(access, quizzes),
        clock
    )
    val tools = MoodleMcpTools(application, Messages(Locale.ENGLISH))

    init {
        access.login(AccessToken(token))
    }
}
