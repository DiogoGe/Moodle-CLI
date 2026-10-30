package moodle.infrastructure.api.dtos

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class PublicConfigRequestDto(
    val index: Int = 0,
    val methodname: String = "tool_mobile_get_public_config",
    val args: JsonObject = JsonObject(emptyMap())
)
