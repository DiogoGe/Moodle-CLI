package moodle.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class McpPage(arguments: McpArguments) {
    val offset = arguments.number("offset", 0, 0, Int.MAX_VALUE)
    val limit = arguments.number("limit", 25, 1, 100)

    fun <T> render(items: List<T>, transform: (T) -> JsonElement): JsonObject {
        val selected = items.drop(offset).take(limit)
        val next = (offset + selected.size).takeIf { selected.isNotEmpty() && it < items.size }
        return buildJsonObject {
            put("items", JsonArray(selected.map(transform)))
            put("total", items.size)
            put("offset", offset)
            put("next_offset", next?.let(::JsonPrimitive) ?: JsonNull)
        }
    }
}
