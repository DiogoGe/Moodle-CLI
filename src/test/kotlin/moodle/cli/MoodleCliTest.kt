package moodle.cli

import moodle.application.MoodleService
import moodle.domain.Assignment
import moodle.domain.Course
import moodle.domain.MoodleException
import moodle.domain.MoodleRepository
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoodleCliTest {
    @Test
    fun `help succeeds without contacting the repository`() {
        val output = mutableListOf<String>()
        val cli = MoodleCli(MoodleService(UnavailableRepository(), Clock.systemUTC()), output::add)

        assertEquals(0, cli.run(arrayOf("--help")))
        assertTrue(output.single().contains("application skeleton"))
    }

    @Test
    fun `unavailable integration is reported as a failure instead of an empty course list`() {
        val output = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val cli = MoodleCli(
            MoodleService(UnavailableRepository(), Clock.systemUTC()),
            output::add,
            errors::add,
        )

        assertEquals(1, cli.run(arrayOf("courses")))
        assertTrue(output.isEmpty())
        assertTrue(errors.single().contains("not implemented"))
    }

    private class UnavailableRepository : MoodleRepository {
        override fun getCourses(): List<Course> =
            throw MoodleException.FeatureUnavailable("Course API integration")
        override fun getAssignments(): List<Assignment> =
            throw MoodleException.FeatureUnavailable("Assignment API integration")
    }
}
