package moodle.cli

import moodle.domain.exceptions.MoodleException

/** Handles quoted destination directories without invoking a shell or expanding variables. */
internal object SessionArguments {
    fun parse(line: String): List<String> {
        val arguments = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var started = false
        for (character in line) {
            when {
                character == quote -> quote = null
                quote != null -> current.append(character)
                character == '\'' || character == '"' -> {
                    quote = character
                    started = true
                }
                character.isWhitespace() -> {
                    if (started) arguments.add(current.toString())
                    current.clear()
                    started = false
                }
                else -> {
                    current.append(character)
                    started = true
                }
            }
        }
        if (quote != null) throw MoodleException.Input("error.input.quote")
        if (started) arguments.add(current.toString())
        return arguments
    }
}
