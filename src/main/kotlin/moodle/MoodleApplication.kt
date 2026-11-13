package moodle

import kotlinx.serialization.json.Json
import moodle.application.AccessService
import moodle.application.AgendaService
import moodle.application.AssignmentService
import moodle.application.CourseService
import moodle.application.QuizService
import moodle.config.MoodleConfig
import moodle.infrastructure.api.RetrofitMoodleAccess
import moodle.infrastructure.api.createMoodleApi
import moodle.infrastructure.api.repository.RetrofitAssignmentRepository
import moodle.infrastructure.api.repository.RetrofitCourseRepository
import moodle.infrastructure.api.repository.RetrofitQuizRepository
import moodle.infrastructure.credentials.platformTokenStore
import moodle.infrastructure.files.LocalCourseFiles
import moodle.infrastructure.files.MoodleFileClient
import moodle.presentation.PlainText
import java.time.Clock

/** Composition shared by the terminal and local MCP entry points. */
internal class MoodleApplication(
    val access: AccessService,
    val courses: CourseService,
    val assignments: AssignmentService,
    val quizzes: QuizService,
    clock: Clock
) {
    val agenda = AgendaService(assignments, quizzes, clock)
}

internal fun createMoodleApplication(useKeychain: Boolean, config: MoodleConfig = MoodleConfig()): MoodleApplication {
    val site = config.baseUrl
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    val api = createMoodleApi(site, json)
    val tokens = if (useKeychain) platformTokenStore() else null
    val access = AccessService(RetrofitMoodleAccess(api, json), site, tokens)
    val clock = Clock.systemDefaultZone()
    return MoodleApplication(
        access,
        CourseService(access, RetrofitCourseRepository(api, json), LocalCourseFiles(MoodleFileClient(site)), PlainText()::render),
        AssignmentService(
            access,
            RetrofitAssignmentRepository(api, json, access::getAvailableFunctions),
            clock
        ),
        QuizService(access, RetrofitQuizRepository(api, json, access::getAvailableFunctions)),
        clock
    )
}
