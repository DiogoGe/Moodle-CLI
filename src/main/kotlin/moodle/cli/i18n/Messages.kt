package moodle.cli.i18n

import moodle.domain.exceptions.MoodleException
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

/** Translation belongs to presentation. Unsupported locales fall back to English, never to the JVM's other defaults. */
class Messages(locale: Locale = Locale.getDefault()) {
    private val language = if (locale.language == "de") Locale.GERMAN else Locale.ENGLISH
    private val bundle = ResourceBundle.getBundle(
        "i18n.messages",
        language,
        ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)
    )

    fun text(key: String, vararg arguments: Any): String {
        // IDs and byte counts must stay copyable without locale-specific digit grouping.
        val values = arguments.map { it.toString() }.toTypedArray()
        return MessageFormat(bundle.getString(key), language).format(values)
    }

    fun error(error: MoodleException): String = text(error.messageKey, *error.arguments.toTypedArray())

    fun boolean(value: Boolean): String = text(if (value) "value.yes" else "value.no")
}
