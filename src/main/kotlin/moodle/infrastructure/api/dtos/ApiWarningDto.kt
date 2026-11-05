package moodle.infrastructure.api.dtos

import kotlinx.serialization.Serializable

/** Keep only the warning code; upstream warning text may include private details. */
@Serializable
internal data class ApiWarningDto(val warningcode: String)
