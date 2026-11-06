package moodle.mcp

import moodle.domain.entities.CourseModule
import moodle.domain.entities.CourseSection

internal data class McpActivity(val section: CourseSection, val module: CourseModule)
