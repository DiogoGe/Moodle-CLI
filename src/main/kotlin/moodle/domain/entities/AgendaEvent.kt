package moodle.domain.entities

import moodle.domain.enums.AgendaEventKind
import java.time.Instant

data class AgendaEvent(
    val kind: AgendaEventKind,
    val activityId: Long,
    val courseId: Long,
    val name: String,
    val at: Instant
)
