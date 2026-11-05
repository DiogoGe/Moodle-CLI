package moodle.infrastructure.windows

import com.sun.jna.Memory

/** Keep normal OS configuration but do not inherit a browser log-file override. */
internal fun windowsBrowserEnvironment(environment: Map<String, String> = System.getenv()): Memory {
    val block = environment.entries
        .filterNot { it.key.equals("CHROME_LOG_FILE", ignoreCase = true) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key })
        .joinToString("\u0000", postfix = "\u0000\u0000") { "${it.key}=${it.value}" }
    val bytes = block.toByteArray(Charsets.UTF_16LE)
    try {
        return Memory(bytes.size.toLong()).apply { write(0, bytes, 0, bytes.size) }
    } finally {
        bytes.fill(0)
    }
}
