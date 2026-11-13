package moodle.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import moodle.domain.exceptions.MoodleException

/** Validate independently of client-side JSON Schema; never echo rejected argument values. */
internal class McpArguments(private val values: JsonObject, allowed: Set<String>) {
    init {
        if (values.keys.any { it !in allowed }) invalid()
    }

    fun id(name: String): Long = optionalId(name) ?: invalid()

    fun optionalId(name: String): Long? = integer(name)?.takeIf { it > 0 } ?: if (name in values) invalid() else null

    fun requiredString(name: String): String {
        val value = values[name] as? JsonPrimitive ?: invalid()
        if (!value.isString) invalid()
        return value.content
    }

    fun requiredObjects(name: String, maximum: Int, allowed: Set<String>): List<McpArguments> {
        val array = values[name] as? JsonArray ?: invalid()
        if (array.size !in 1..maximum) invalid()
        return array.map { item -> McpArguments(item as? JsonObject ?: invalid(), allowed) }
    }

    fun boolean(name: String, default: Boolean): Boolean {
        val value = values[name] ?: return default
        if (value !is JsonPrimitive || value.isString) invalid()
        return value.booleanOrNull ?: invalid()
    }

    fun requiredNumber(name: String, minimum: Int, maximum: Int): Int =
        boundedNumber(integer(name) ?: invalid(), minimum, maximum)

    fun optionalNumber(name: String, minimum: Int, maximum: Int): Int? =
        integer(name)?.let { boundedNumber(it, minimum, maximum) }

    fun number(
        name: String,
        default: Int,
        minimum: Int,
        maximum: Int
    ): Int = boundedNumber(integer(name) ?: default.toLong(), minimum, maximum)

    private fun boundedNumber(value: Long, minimum: Int, maximum: Int): Int {
        if (value !in minimum.toLong()..maximum.toLong()) invalid()
        return value.toInt()
    }

    private fun integer(name: String): Long? {
        if (name !in values) return null
        val value = values[name] as? JsonPrimitive ?: invalid()
        if (value.isString) invalid()
        return value.longOrNull ?: invalid()
    }

    private fun invalid(): Nothing = throw MoodleException.Input("error.mcp.arguments")
}
