package moodle.cli

import moodle.domain.exceptions.MoodleException
import java.util.Locale

/** Global options precede the command and do not modify the JVM's global locale. */
data class CliOptions(val locale: Locale, val arguments: List<String>) {
    companion object {
        fun parse(args: Array<String>, systemLocale: Locale = Locale.getDefault()): CliOptions {
            if (args.firstOrNull() != "--lang") return CliOptions(systemLocale, args.toList())
            val language = args.getOrNull(1) ?: throw MoodleException.Input("error.language.missing")
            val locale = when (language) {
                "de" -> Locale.GERMAN
                "en" -> Locale.ENGLISH
                else -> throw MoodleException.Input("error.language.unsupported")
            }
            return CliOptions(locale, args.drop(2))
        }
    }
}
