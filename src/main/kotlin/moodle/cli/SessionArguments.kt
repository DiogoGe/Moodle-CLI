package moodle.cli

import moodle.domain.exceptions.MoodleException

/** Handles quoted destination directories without invoking a shell or expanding variables. */
internal object SessionArguments {
    fun parse(line: String): List<String> {
        val arguments = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        for (character in line) {
            when {
                character == quote -> quote = null
                quote != null -> current.append(character)
                character == '\'' || character == '"' -> quote = character
                character.isWhitespace() -> {
                    if (current.isNotEmpty()) arguments.add(current.toString())
                    current.clear()
                }
                else -> current.append(character)
            }
        }
        if (quote != null) throw MoodleException.Input("error.input.quote")
        if (current.isNotEmpty()) arguments.add(current.toString())
        return arguments
    }
}
