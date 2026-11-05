package moodle.infrastructure.api.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import moodle.domain.AccessToken
import moodle.domain.entities.Assignment
import moodle.domain.entities.AssignmentList
import moodle.domain.entities.SubmissionDetails
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.AssignmentRepository
import moodle.infrastructure.api.dtos.AssignmentListDto
import moodle.infrastructure.api.dtos.SubmissionDetailsDto
import moodle.infrastructure.api.interfaces.MoodleApi
import moodle.infrastructure.api.readMoodle
import moodle.infrastructure.api.toDomain

internal class RetrofitAssignmentRepository(
    private val api: MoodleApi,
    private val json: Json,
    private val availableFunctions: () -> Set<String>
) : AssignmentRepository {
    override fun getAssignments(token: AccessToken, courseId: Long?): AssignmentList {
        if (LIST_FUNCTION !in availableFunctions()) throw MoodleException.Permission("error.api.assignmentsUnavailable")
        val fields = courseId?.let { mapOf("courseids[0]" to it.toString()) }.orEmpty()
        return api.getAssignments(token.value, fields).readMoodle("error.api.assignmentResponse") { body ->
            json.decodeFromJsonElement<AssignmentListDto>(body).toDomain()
        }
    }

    override fun getSubmissionDetails(token: AccessToken, assignment: Assignment): SubmissionDetails {
        if (STATUS_FUNCTION !in availableFunctions()) return SubmissionDetails()
        try {
            return api.getSubmissionStatus(token.value, assignment.id).readMoodle("error.api.assignmentResponse") { body ->
                json.decodeFromJsonElement<SubmissionDetailsDto>(body).toDomain(assignment.groupSubmission)
            }
        } catch (_: MoodleException.Permission) {
            // A missing optional status capability must not hide otherwise accessible assignments.
            return SubmissionDetails()
        }
    }

    private companion object {
        const val LIST_FUNCTION = "mod_assign_get_assignments"
        const val STATUS_FUNCTION = "mod_assign_get_submission_status"
    }
}
