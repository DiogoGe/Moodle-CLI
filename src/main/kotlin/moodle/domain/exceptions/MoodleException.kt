package moodle.domain.exceptions

/** Stable failure identifiers; the CLI translates them without retaining upstream error messages. */
sealed class MoodleException(val messageKey: String, vararg arguments: Any) : RuntimeException(messageKey) {
    val arguments: List<Any> = arguments.toList()

    class Input(key: String, vararg arguments: Any) : MoodleException(key, *arguments)

    class Credentials(key: String, vararg arguments: Any) : MoodleException(key, *arguments)

    class Permission(key: String, vararg arguments: Any) : MoodleException(key, *arguments)

    class Download(key: String, vararg arguments: Any) : MoodleException(key, *arguments)

    class Authentication(key: String, vararg arguments: Any) : MoodleException(key, *arguments)

    class InvalidCallback(key: String, vararg arguments: Any) : MoodleException(key, *arguments)

    class Connection(key: String, vararg arguments: Any) : MoodleException(key, *arguments)

    class FeatureUnavailable(feature: String) : MoodleException("error.featureUnavailable", feature)
}
