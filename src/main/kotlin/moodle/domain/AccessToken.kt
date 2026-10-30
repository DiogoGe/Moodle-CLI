package moodle.domain

/** A process-local secret. Never log its value or persist it as configuration. */
class AccessToken(val value: String) {
    init {
        require(Regex("[a-fA-F0-9]{32}").matches(value)) { "Invalid Moodle token format." }
    }

    override fun toString(): String = "AccessToken([redacted])"
}
