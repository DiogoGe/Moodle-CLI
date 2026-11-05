package moodle.domain.entities

import java.time.Duration
import java.time.Instant

/** Within a known schedule, null dates or duration mean no corresponding restriction. */
data class QuizTiming(val opensAt: Instant?, val closesAt: Instant?, val timeLimit: Duration?)
