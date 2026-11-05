package moodle.infrastructure.browser

import java.io.InputStream
import java.io.OutputStream

/** Owns the browser process and its private protocol streams. */
internal interface ChromeTransport : AutoCloseable {
    val input: InputStream
    val output: OutputStream
    val isAlive: Boolean
}
