package moodle.cli

import moodle.application.MoodleService
import moodle.domain.entities.Assignment
import moodle.domain.entities.Course
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.MoodleRepository
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
        assertTrue(output.single().contains("Usage: moodle"))
    }

    @Test
    fun `unavailable integration is reported as a failure instead of an empty course list`() {
        val output = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val cli = MoodleCli(
            MoodleService(UnavailableRepository(), Clock.systemUTC()),
            output::add,
            errors::add
        )

        assertEquals(1, cli.run(arrayOf("courses")))
        assertTrue(output.isEmpty())
        assertTrue(errors.single().contains("not implemented"))
    }

    @Test
    fun `automatic and manual login dispatch explicitly`() {
        val calls = mutableListOf<String>()
        val cli = MoodleCli(
            MoodleService(UnavailableRepository(), Clock.systemUTC()),
            accessCommand = {
                calls.add(it)
                0
            }
        )
        assertEquals(0, cli.run(arrayOf("login")))
        assertEquals(0, cli.run(arrayOf("login", "--manual")))
        assertEquals(listOf("login", "login-manual"), calls)
    }

    private class UnavailableRepository : MoodleRepository {
        override fun getCourses(): List<Course> = throw MoodleException.FeatureUnavailable("Course API integration")

        override fun getAssignments(): List<Assignment> = throw MoodleException.FeatureUnavailable("Assignment API integration")
    }
}
