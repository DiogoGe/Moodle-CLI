package moodle.mcp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import moodle.domain.exceptions.MoodleException

/** Validate independently of client-side JSON Schema; never echo rejected argument values. */
internal class McpArguments(private val values: JsonObject, allowed: Set<String>) {
    init {
        if (values.keys.any { it !in allowed }) invalid()
    }

    fun id(name: String): Long = optionalId(name) ?: invalid()

    fun optionalId(name: String): Long? = integer(name)?.takeIf { it > 0 } ?: if (name in values) invalid() else null

    fun requiredNumber(name: String, minimum: Int, maximum: Int): Int =
        boundedNumber(integer(name) ?: invalid(), minimum, maximum)

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
