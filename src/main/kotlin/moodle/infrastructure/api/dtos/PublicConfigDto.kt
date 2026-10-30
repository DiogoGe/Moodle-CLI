package moodle.infrastructure.api.dtos

import kotlinx.serialization.Serializable

@Serializable
internal data class PublicConfigDto(
    val wwwroot: String,
    val sitename: String,
    val enablewebservices: Int,
    val enablemobilewebservice: Int,
    val typeoflogin: Int,
    val maintenanceenabled: Int
)
