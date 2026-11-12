package moodle.presentation

import org.jsoup.Jsoup

/** Parses HTML locally; never fetches images, scripts, stylesheets, or links. */
internal class PlainText {
    fun render(html: String): String {
        val document = Jsoup.parseBodyFragment(html)
        document.select("script, style, iframe, object, template").remove()
        document.select("p, div, li, h1, h2, h3, h4, tr, pre, blockquote").forEach {
            it.before("\n")
            it.after("\n")
        }
        val text = document.body().wholeText().replace('\u00a0', ' ')
        return text
            .filter { it == '\n' || it == '\t' || (!it.isISOControl() && Character.getType(it) != Character.FORMAT.toInt()) }
            .lines()
            .map { it.trim().replace(HORIZONTAL_WHITESPACE, " ") }
            .joinToString("\n")
            .replace(EXCESS_LINE_BREAKS, "\n\n")
            .trim()
    }

    fun label(value: String): String = render(value).replace('\n', ' ')

    private companion object {
        val HORIZONTAL_WHITESPACE = Regex("[ \t]+")
        val EXCESS_LINE_BREAKS = Regex("\n{3,}")
    }
}
