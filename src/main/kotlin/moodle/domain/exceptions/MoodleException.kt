package moodle.domain.exceptions

sealed class MoodleException(message: String) : RuntimeException(message) {
    class Input(message: String) : MoodleException(message)

    class Download(message: String) : MoodleException(message)

    class Authentication(message: String) : MoodleException(message)

    class InvalidCallback(message: String) : MoodleException(message)

    class Connection(message: String) : MoodleException(message)

    class FeatureUnavailable(feature: String) : MoodleException("$feature is not implemented yet.")
}
