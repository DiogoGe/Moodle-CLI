package moodle.domain.entities

/** Partial results are explicitly marked when Moodle reports inaccessible courses or activities. */
data class AssignmentList(val items: List<Assignment>, val partial: Boolean = false)
