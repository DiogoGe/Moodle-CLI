package moodle.domain

sealed class MoodleException(
    message: String,
) : RuntimeException(message) {
    class FeatureUnavailable(
        feature: String,
    ) : MoodleException("$feature is not implemented yet.")
}
