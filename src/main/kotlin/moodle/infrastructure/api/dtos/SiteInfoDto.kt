package moodle.infrastructure.api.dtos

import kotlinx.serialization.Serializable

@Serializable
internal data class SiteInfoDto(
    val siteurl: String,
    val sitename: String,
    val userid: Long,
    val fullname: String,
    val functions: List<FunctionDto>
)
