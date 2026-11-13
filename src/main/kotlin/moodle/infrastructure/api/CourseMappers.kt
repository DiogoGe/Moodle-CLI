package moodle.infrastructure.api

import moodle.domain.entities.CourseFile
import moodle.domain.entities.CourseModule
import moodle.domain.entities.CourseSection
import moodle.infrastructure.api.dtos.CourseContentDto
import moodle.infrastructure.api.dtos.CourseModuleDto
import moodle.infrastructure.api.dtos.CourseSectionDto

internal fun CourseSectionDto.toDomain(): CourseSection = CourseSection(
    id,
    name,
    summary,
    modules.filter { uservisible && it.uservisible }.map { it.toDomain() },
    userVisible = uservisible
)

private fun CourseModuleDto.toDomain(): CourseModule = CourseModule(
    id,
    name,
    modname,
    description,
    contents.filter { it.type == "content" }.mapNotNull { it.content },
    contents.filter { it.type == "file" }.mapNotNull { it.toDomain() },
    noViewLink = noviewlink
)

private fun CourseContentDto.toDomain(): CourseFile? = fileurl?.let { CourseFile(filename, it, filesize, mimetype) }
