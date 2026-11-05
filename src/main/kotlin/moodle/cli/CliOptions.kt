package moodle.cli

import moodle.domain.exceptions.MoodleException
import java.util.Locale

/** Global options precede the command and do not modify the JVM's global locale. */
data class CliOptions(val locale: Locale, val arguments: List<String>, val useKeychain: Boolean = true) {
    companion object {
        fun parse(args: Array<String>, systemLocale: Locale = Locale.getDefault()): CliOptions {
            var locale = systemLocale
            var keychain = true
            var index = 0
            while (index < args.size) {
                when (args[index]) {
                    "--no-store" -> {
                        keychain = false
                        index++
                    }
                    "--lang" -> {
                        locale = language(args.getOrNull(index + 1))
                        index += 2
                    }
                    else -> break
                }
            }
            return CliOptions(locale, args.drop(index), keychain)
        }

        private fun language(value: String?): Locale = when (value) {
            null -> throw MoodleException.Input("error.language.missing")
            "de" -> Locale.GERMAN
            "en" -> Locale.ENGLISH
            else -> throw MoodleException.Input("error.language.unsupported")
        }
    }
}
