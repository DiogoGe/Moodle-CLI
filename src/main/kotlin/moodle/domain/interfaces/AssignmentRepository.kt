package moodle.domain.interfaces

import moodle.domain.AccessToken
import moodle.domain.entities.Assignment
import moodle.domain.entities.AssignmentList
import moodle.domain.entities.SubmissionDetails

interface AssignmentRepository {
    fun getAssignments(token: AccessToken, courseId: Long?): AssignmentList

    fun getSubmissionDetails(token: AccessToken, assignment: Assignment): SubmissionDetails
}
