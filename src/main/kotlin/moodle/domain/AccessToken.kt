package moodle.domain

/** A secret value. Never log it or persist it as ordinary configuration. */
class AccessToken(val value: String) {
    init {
        require(Regex("[a-fA-F0-9]{32}").matches(value)) { "Invalid Moodle token format." }
    }

    override fun toString(): String = "AccessToken([redacted])"
}
