package moodle.infrastructure.api.dtos

import kotlinx.serialization.Serializable

@Serializable
internal data class PublicConfigEnvelopeDto(val error: Boolean, val data: PublicConfigDto? = null)
