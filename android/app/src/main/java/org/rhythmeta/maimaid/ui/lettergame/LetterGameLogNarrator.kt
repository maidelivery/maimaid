package org.rhythmeta.maimaid.ui.lettergame

import org.rhythmeta.maimaid.core.data.LetterGameLogEntry

internal enum class LetterGameLogTemplate {
    OPEN_NO_PROGRESS_1,
    OPEN_NO_PROGRESS_2,
    OPEN_NO_PROGRESS_STREAK_1,
    OPEN_NO_PROGRESS_STREAK_2,
    OPEN_REPEATED_1,
    OPEN_REPEATED_2,
    OPEN_FEW_1,
    OPEN_FEW_2,
    OPEN_MANY_1,
    OPEN_MANY_2,
    OPEN_SUCCESS_STREAK_1,
    OPEN_SUCCESS_STREAK_2,
    GUESS_BLIND_1,
    GUESS_BLIND_2,
    GUESS_CORRECT_1,
    GUESS_CORRECT_2,
    GUESS_CORRECT_STREAK_1,
    GUESS_CORRECT_STREAK_2,
    GUESS_INCORRECT_1,
    GUESS_INCORRECT_2,
    GUESS_INCORRECT_STREAK_1,
    GUESS_INCORRECT_STREAK_2,
    PROGRESS_RECOVERED_1,
    PROGRESS_RECOVERED_2,
    HINT_PUBLIC_1,
    HINT_PUBLIC_2,
    HINT_PRIVATE_1,
    HINT_PRIVATE_2,
    HINT_LOW_SCORE_1,
    HINT_LOW_SCORE_2,
    HINT_NORMAL_1,
    HINT_NORMAL_2,
    FALLBACK,
}

internal data class LetterGameLogNarration(
    val logId: String,
    val template: LetterGameLogTemplate,
    val actor: String = "Player",
    val character: String = "",
    val count: Int = 0,
    val points: Int = 0,
    val streak: Int = 0,
    val cost: Int = 0,
    val balance: Int = 0,
    val hintCount: Int = 0,
    val hintType: String = "hint",
    val hintResult: Boolean? = null,
    val fallbackMessage: String? = null,
)

internal object LetterGameLogNarrator {
    fun narrate(logs: List<LetterGameLogEntry>): List<LetterGameLogNarration> {
        val state = ReplayState()
        return logs.map { log -> state.narrate(log) }
    }

    private class ReplayState {
        private val successStreaks = mutableMapOf<String, Int>()
        private val failureStreaks = mutableMapOf<String, Int>()
        private val balances = mutableMapOf<String, Int>()
        private val hintCounts = mutableMapOf<String, Int>()
        private val openedCharacters = mutableSetOf<Char>()
        private var noProgressStreak = 0

        fun narrate(log: LetterGameLogEntry): LetterGameLogNarration {
            val actorKey = log.actorUserId?.takeUnless { it.isBlank() }
                ?: log.actorName?.takeUnless { it.isBlank() }
                ?: "unknown"
            val actor = log.actorName?.takeUnless { it.isBlank() }
                ?: log.actorUserId?.takeUnless { it.isBlank() }
                ?: "Player"
            val previousNoProgress = noProgressStreak
            val previousSuccess = successStreaks[actorKey] ?: 0
            val previousFailure = failureStreaks[actorKey] ?: 0
            return when (log.actionType) {
                "open_character" -> narrateOpen(log, actorKey, actor, previousNoProgress, previousSuccess)
                "guess_song" -> narrateGuess(log, actorKey, actor, previousNoProgress, previousSuccess, previousFailure)
                "buy_hint" -> narrateHint(log, actorKey, actor)
                else -> LetterGameLogNarration(
                    logId = log.id,
                    template = LetterGameLogTemplate.FALLBACK,
                    actor = actor,
                    fallbackMessage = log.message,
                )
            }
        }

        private fun narrateOpen(
            log: LetterGameLogEntry,
            actorKey: String,
            actor: String,
            previousNoProgress: Int,
            previousSuccess: Int,
        ): LetterGameLogNarration {
            val count = (log.newlyRevealedCount ?: 0).coerceAtLeast(0)
            val points = (log.points ?: count).coerceAtLeast(0)
            val character = log.character.orEmpty()
            val normalizedCharacter = character.firstOrNull()?.uppercaseChar()
            val repeated = normalizedCharacter != null && !openedCharacters.add(normalizedCharacter)
            val narration = when {
                count == 0 && previousNoProgress >= 2 -> choose(
                    log,
                    listOf(LetterGameLogTemplate.OPEN_NO_PROGRESS_STREAK_1, LetterGameLogTemplate.OPEN_NO_PROGRESS_STREAK_2),
                    actor = actor,
                    streak = previousNoProgress + 1,
                )
                count == 0 -> choose(
                    log,
                    listOf(LetterGameLogTemplate.OPEN_NO_PROGRESS_1, LetterGameLogTemplate.OPEN_NO_PROGRESS_2),
                    actor = actor,
                    character = character,
                )
                previousNoProgress >= 2 -> choose(
                    log,
                    listOf(LetterGameLogTemplate.PROGRESS_RECOVERED_1, LetterGameLogTemplate.PROGRESS_RECOVERED_2),
                    actor = actor,
                    count = previousNoProgress,
                    points = points,
                )
                repeated -> choose(
                    log,
                    listOf(LetterGameLogTemplate.OPEN_REPEATED_1, LetterGameLogTemplate.OPEN_REPEATED_2),
                    actor = actor,
                    character = character,
                    count = count,
                    points = points,
                )
                previousSuccess >= 2 -> choose(
                    log,
                    listOf(LetterGameLogTemplate.OPEN_SUCCESS_STREAK_1, LetterGameLogTemplate.OPEN_SUCCESS_STREAK_2),
                    actor = actor,
                    count = count,
                    streak = previousSuccess + 1,
                    points = points,
                )
                count >= 4 -> choose(
                    log,
                    listOf(LetterGameLogTemplate.OPEN_MANY_1, LetterGameLogTemplate.OPEN_MANY_2),
                    actor = actor,
                    count = count,
                    points = points,
                )
                else -> choose(
                    log,
                    listOf(LetterGameLogTemplate.OPEN_FEW_1, LetterGameLogTemplate.OPEN_FEW_2),
                    actor = actor,
                    count = count,
                    points = points,
                )
            }
            if (count == 0) {
                noProgressStreak = previousNoProgress + 1
                successStreaks[actorKey] = 0
                failureStreaks[actorKey] = (failureStreaks[actorKey] ?: 0) + 1
            } else {
                noProgressStreak = 0
                successStreaks[actorKey] = previousSuccess + 1
                failureStreaks[actorKey] = 0
            }
            balances[actorKey] = log.balance ?: ((balances[actorKey] ?: 0) + points)
            return narration
        }

        private fun narrateGuess(
            log: LetterGameLogEntry,
            actorKey: String,
            actor: String,
            previousNoProgress: Int,
            previousSuccess: Int,
            previousFailure: Int,
        ): LetterGameLogNarration {
            val points = (log.points ?: 0).coerceAtLeast(0)
            val correct = log.correct == true
            val narration = if (correct) {
                when {
                    previousNoProgress >= 2 -> choose(
                        log,
                        listOf(LetterGameLogTemplate.PROGRESS_RECOVERED_1, LetterGameLogTemplate.PROGRESS_RECOVERED_2),
                        actor = actor,
                        count = previousNoProgress,
                        points = points,
                    )
                    log.blind == true -> choose(
                        log,
                        listOf(LetterGameLogTemplate.GUESS_BLIND_1, LetterGameLogTemplate.GUESS_BLIND_2),
                        actor = actor,
                        points = points,
                    )
                    previousSuccess >= 2 -> choose(
                        log,
                        listOf(LetterGameLogTemplate.GUESS_CORRECT_STREAK_1, LetterGameLogTemplate.GUESS_CORRECT_STREAK_2),
                        actor = actor,
                        streak = previousSuccess + 1,
                        points = points,
                    )
                    else -> choose(
                        log,
                        listOf(LetterGameLogTemplate.GUESS_CORRECT_1, LetterGameLogTemplate.GUESS_CORRECT_2),
                        actor = actor,
                        points = points,
                    )
                }
            } else if (previousFailure >= 1) {
                choose(
                    log,
                    listOf(LetterGameLogTemplate.GUESS_INCORRECT_STREAK_1, LetterGameLogTemplate.GUESS_INCORRECT_STREAK_2),
                    actor = actor,
                    streak = previousFailure + 1,
                )
            } else {
                choose(
                    log,
                    listOf(LetterGameLogTemplate.GUESS_INCORRECT_1, LetterGameLogTemplate.GUESS_INCORRECT_2),
                    actor = actor,
                )
            }
            if (correct) {
                noProgressStreak = 0
                successStreaks[actorKey] = previousSuccess + 1
                failureStreaks[actorKey] = 0
                balances[actorKey] = log.balance ?: ((balances[actorKey] ?: 0) + points)
            } else {
                noProgressStreak = previousNoProgress + 1
                successStreaks[actorKey] = 0
                failureStreaks[actorKey] = previousFailure + 1
            }
            return narration
        }

        private fun narrateHint(log: LetterGameLogEntry, actorKey: String, actor: String): LetterGameLogNarration {
            val cost = (log.hintCost ?: log.points ?: 0).coerceAtLeast(0)
            val beforeBalance = balances[actorKey] ?: log.balance?.plus(cost) ?: 0
            val afterBalance = log.balance ?: beforeBalance - cost
            val hasBalance = balances.containsKey(actorKey) || log.balance != null
            balances[actorKey] = afterBalance
            val hintCount = (hintCounts[actorKey] ?: 0) + 1
            hintCounts[actorKey] = hintCount
            val template = when {
                hasBalance && afterBalance <= cost * 2 && cost > 0 -> listOf(LetterGameLogTemplate.HINT_LOW_SCORE_1, LetterGameLogTemplate.HINT_LOW_SCORE_2)
                log.hintVisibility == "private" -> listOf(LetterGameLogTemplate.HINT_PRIVATE_1, LetterGameLogTemplate.HINT_PRIVATE_2)
                log.hintVisibility == "public" -> listOf(LetterGameLogTemplate.HINT_PUBLIC_1, LetterGameLogTemplate.HINT_PUBLIC_2)
                else -> listOf(LetterGameLogTemplate.HINT_NORMAL_1, LetterGameLogTemplate.HINT_NORMAL_2)
            }
            return choose(
                log,
                template,
                actor = actor,
                cost = cost,
                balance = afterBalance,
                hintCount = hintCount,
                hintType = log.hintType.orEmpty().ifBlank { "hint" },
                hintResult = log.hintResult,
            )
        }

        private fun choose(
            log: LetterGameLogEntry,
            templates: List<LetterGameLogTemplate>,
            actor: String,
            character: String = "",
            count: Int = 0,
            points: Int = 0,
            streak: Int = 0,
            cost: Int = 0,
            balance: Int = 0,
            hintCount: Int = 0,
            hintType: String = "hint",
            hintResult: Boolean? = null,
        ): LetterGameLogNarration {
            val index = stableIndex("${log.id}|${log.actionType}|${templates.first()}", templates.size)
            return LetterGameLogNarration(
                logId = log.id,
                template = templates[index],
                actor = actor,
                character = character,
                count = count,
                points = points,
                streak = streak,
                cost = cost,
                balance = balance,
                hintCount = hintCount,
                hintType = hintType,
                hintResult = hintResult,
            )
        }
    }

    private fun stableIndex(value: String, size: Int): Int {
        var hash = 0x811C9DC5.toInt()
        value.forEach { character ->
            hash = (hash xor character.code) * 16777619
        }
        return (hash and Int.MAX_VALUE) % size
    }
}
