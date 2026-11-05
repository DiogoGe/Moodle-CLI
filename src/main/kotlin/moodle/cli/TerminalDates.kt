package moodle.cli

import moodle.cli.i18n.Messages
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Keeps assignment and quiz dates consistent, including local UTC offset and zone name. */
internal class TerminalDates(clock: Clock, messages: Messages) {
    private val dates = DateTimeFormatter.ofPattern(messages.text("date.pattern"), Locale.ROOT).withZone(clock.zone)

    fun format(value: Instant): String = dates.format(value)
}
