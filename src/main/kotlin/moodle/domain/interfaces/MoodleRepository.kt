package moodle.domain.interfaces

import moodle.domain.entities.Assignment

interface MoodleRepository {
    fun getAssignments(): List<Assignment>
}
