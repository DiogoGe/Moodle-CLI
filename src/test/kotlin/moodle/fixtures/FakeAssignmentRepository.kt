package moodle.fixtures

import moodle.domain.AccessToken
import moodle.domain.entities.Assignment
import moodle.domain.entities.AssignmentList
import moodle.domain.entities.SubmissionDetails
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.AssignmentRepository

class FakeAssignmentRepository(var result: AssignmentList) : AssignmentRepository {
    val details = mutableMapOf<Long, SubmissionDetails>()
    val statusCalls = mutableListOf<Long>()
    var listCalls = 0
    var requestedCourse: Long? = null
    var receivedToken: AccessToken? = null
    var expired = false

    override fun getAssignments(token: AccessToken, courseId: Long?): AssignmentList {
        listCalls++
        receivedToken = token
        requestedCourse = courseId
        if (expired) throw MoodleException.Authentication("error.session.expired")
        return result
    }

    override fun getSubmissionDetails(token: AccessToken, assignment: Assignment): SubmissionDetails {
        statusCalls.add(assignment.id)
        return details[assignment.id] ?: SubmissionDetails()
    }
}
