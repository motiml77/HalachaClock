package com.zmanimclock.app.feature.alarm

import com.zmanimclock.app.feature.alarms.data.DismissChallenge
import kotlin.random.Random

/**
 * The dismiss-gate problem: the alarm's אישור button unlocks only after a
 * correct answer. Wrong answer → a fresh problem. Snooze is never gated —
 * a groggy user must always have a safe way out.
 */
data class MathProblem(val text: String, val answer: Int)

object MathChallenge {

    fun generate(level: DismissChallenge, random: Random = Random.Default): MathProblem? =
        when (level) {
            DismissChallenge.NONE -> null
            DismissChallenge.MATH_EASY -> {
                val a = random.nextInt(2, 10)
                val b = random.nextInt(2, 10)
                MathProblem("$a + $b = ?", a + b)
            }
            DismissChallenge.MATH_MEDIUM -> {
                val a = random.nextInt(12, 50)
                val b = random.nextInt(12, 50)
                MathProblem("$a + $b = ?", a + b)
            }
            DismissChallenge.MATH_HARD -> {
                val a = random.nextInt(13, 20)
                val b = random.nextInt(6, 10)
                MathProblem("$a × $b = ?", a * b)
            }
        }
}
