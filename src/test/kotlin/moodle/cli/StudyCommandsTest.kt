package moodle.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class StudyCommandsTest {
    @Test
    fun `assignments aliases and quiz commands are dispatched separately with their filters`() {
        val assignmentCalls = mutableListOf<List<String>>()
        val quizCalls = mutableListOf<List<String>>()
        val courseCalls = mutableListOf<List<String>>()
        val commands = StudyCommands(
            {
                courseCalls.add(it)
                0
            },
            {
                assignmentCalls.add(it)
                0
            },
            {
                quizCalls.add(it)
                0
            }
        )
        commands.run(listOf("tasks"))
        commands.run(listOf("assignments", "--course", "42"))
        commands.run(listOf("quizzes", "--course", "42"))
        commands.run(listOf("upcoming", "--days", "7"))
        commands.run(listOf("course", "42"))
        assertEquals(
            listOf(listOf("tasks"), listOf("assignments", "--course", "42"), listOf("upcoming", "--days", "7")),
            assignmentCalls
        )
        assertEquals(listOf(listOf("quizzes", "--course", "42")), quizCalls)
        assertEquals(listOf(listOf("course", "42")), courseCalls)
    }
}
