package moodle.infrastructure.windows

/** Windows CRT quoting, passed directly to CreateProcessW without cmd.exe or PowerShell. */
internal fun windowsCommandLine(arguments: List<String>): CharArray = arguments
    .joinToString(" ", transform = ::quoteWindowsArgument)
    .also { require('\u0000' !in it && it.length < 32767) }
    .let { "$it\u0000".toCharArray() }

private fun quoteWindowsArgument(argument: String): String = buildString {
    append('"')
    var slashes = 0
    for (character in argument) {
        if (character == '\\') {
            slashes++
        } else {
            repeat(if (character == '"') slashes * 2 + 1 else slashes) { append('\\') }
            append(character)
            slashes = 0
        }
    }
    repeat(slashes * 2) { append('\\') }
    append('"')
}
