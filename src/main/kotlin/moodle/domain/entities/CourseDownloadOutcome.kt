package moodle.domain.entities

import moodle.domain.exceptions.MoodleException

sealed interface CourseDownloadOutcome {
    val selection: CourseFileSelection

    data class Saved(override val selection: CourseFileSelection, val file: DownloadedFile) : CourseDownloadOutcome

    data class Failed(override val selection: CourseFileSelection, val error: MoodleException) : CourseDownloadOutcome

    data class Skipped(override val selection: CourseFileSelection) : CourseDownloadOutcome
}
