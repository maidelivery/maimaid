package net.krtl.maimaid.scanner.matching

import net.krtl.maimaid.domain.model.Sheet
import net.krtl.maimaid.domain.model.Song
import net.krtl.maimaid.scanner.model.ScannerImageType
import net.krtl.maimaid.scanner.model.ScannerMatch
import net.krtl.maimaid.scanner.model.ScannerRecognition
import net.krtl.maimaid.scanner.text.ScannerTextParser
import kotlin.math.abs
import kotlin.math.max

class ScannerSongMatcher(
    private val songs: List<Song>
) {
    fun match(recognition: ScannerRecognition): ScannerMatch? {
        val matchedSong = when (recognition.imageType) {
            ScannerImageType.CHOOSE -> matchChoose(recognition)
            ScannerImageType.SCORE -> matchScore(recognition)
            ScannerImageType.UNKNOWN -> null
        } ?: return null

        val matchedSheet = if (recognition.imageType == ScannerImageType.SCORE) {
            resolveScoreSheet(
                song = matchedSong,
                difficulty = recognition.difficulty,
                type = recognition.type,
                kanji = recognition.kanji,
                maxDxScore = recognition.maxDxScore,
                dxScore = recognition.dxScore
            )
        } else {
            null
        }

        return ScannerMatch(
            recognition = recognition,
            song = matchedSong,
            sheet = matchedSheet
        )
    }

    fun matchScoreCandidates(recognition: ScannerRecognition, limit: Int = 6): List<ScannerMatch> {
        if (recognition.imageType != ScannerImageType.SCORE) return emptyList()

        return matchSongsWithFilters(
            titleCandidates = recognition.titleCandidates,
            title = recognition.title,
            difficulty = recognition.difficulty,
            level = recognition.level,
            maxCombo = recognition.maxCombo,
            dxScore = recognition.dxScore,
            maxDxScore = recognition.maxDxScore,
            type = recognition.type,
            kanji = recognition.kanji
        )
            .take(limit)
            .map { song ->
                ScannerMatch(
                    recognition = recognition,
                    song = song,
                    sheet = resolveScoreSheet(
                        song = song,
                        difficulty = recognition.difficulty,
                        type = recognition.type,
                        kanji = recognition.kanji,
                        maxDxScore = recognition.maxDxScore,
                        dxScore = recognition.dxScore
                    )
                )
            }
    }

    fun resolveScoreSheet(
        song: Song,
        difficulty: String?,
        type: String?,
        kanji: String?,
        maxDxScore: Int?,
        dxScore: Int?
    ): Sheet? {
        val normalizedDifficulty = difficulty?.trim()?.lowercase()
        val normalizedType = type?.trim()?.lowercase()

        if (normalizedType == "utage") {
            return matchUtageSheet(song, kanji, maxDxScore, dxScore)
        }

        val candidates = song.sheets.filter { sheet ->
            val sheetType = sheet.type.lowercase()
            if (sheetType == "utage") return@filter false
            if (!normalizedType.isNullOrBlank() && sheetType != normalizedType) return@filter false
            if (!normalizedDifficulty.isNullOrBlank() && sheet.difficulty.lowercase() != normalizedDifficulty) return@filter false
            true
        }

        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        if (maxDxScore != null && maxDxScore > 0) {
            val targetTotal = maxDxScore / 3
            candidates.firstOrNull { it.total == targetTotal }?.let { return it }
        }

        if (dxScore != null && dxScore > 0) {
            val dxCandidates = candidates.filter { sheet ->
                sheet.total?.let { it * 3 >= dxScore } ?: true
            }
            if (dxCandidates.size == 1) return dxCandidates.first()
            dxCandidates.minByOrNull { sheet ->
                sheet.total?.let { abs(it * 3 - dxScore) } ?: Int.MAX_VALUE
            }?.let { return it }
        }

        return candidates.first()
    }

    private fun matchChoose(recognition: ScannerRecognition): Song? {
        // Route through the same OCR-variant-aware scoring pipeline as SCORE images,
        // matching iOS behavior where CHOOSE images use matchSongsWithFilters with no
        // difficulty/type/level constraints.
        val matched = matchSongsWithFilters(
            titleCandidates = recognition.titleCandidates,
            title = recognition.title,
            difficulty = null,
            level = null,
            maxCombo = null,
            dxScore = null,
            maxDxScore = null,
            type = null,
            kanji = null
        )
        return matched.firstOrNull()
    }

    private fun matchScore(recognition: ScannerRecognition): Song? {
        val matchedSongs = matchSongsWithFilters(
            titleCandidates = recognition.titleCandidates,
            title = recognition.title,
            difficulty = recognition.difficulty,
            level = recognition.level,
            maxCombo = recognition.maxCombo,
            dxScore = recognition.dxScore,
            maxDxScore = recognition.maxDxScore,
            type = recognition.type,
            kanji = recognition.kanji
        )

        return matchedSongs.firstOrNull {
            canPresentScoreResult(
                song = it,
                difficulty = recognition.difficulty,
                type = recognition.type,
                kanji = recognition.kanji,
                maxDxScore = recognition.maxDxScore,
                dxScore = recognition.dxScore
            )
        }
    }

    private fun canPresentScoreResult(
        song: Song,
        difficulty: String?,
        type: String?,
        kanji: String?,
        maxDxScore: Int?,
        dxScore: Int?
    ): Boolean {
        val normalizedDifficulty = difficulty?.trim().orEmpty()
        if (normalizedDifficulty.isEmpty()) return true
        return resolveScoreSheet(song, normalizedDifficulty, type, kanji, maxDxScore, dxScore) != null
    }

    private fun matchSongsWithFilters(
        titleCandidates: List<String>,
        title: String?,
        difficulty: String?,
        level: Double?,
        maxCombo: Int?,
        dxScore: Int?,
        maxDxScore: Int?,
        type: String?,
        kanji: String?
    ): List<Song> {
        var allCandidates = buildList {
            title?.let(::add)
            addAll(titleCandidates)
        }
        val isUtage = type?.lowercase() == "utage"
        if (isUtage) {
            allCandidates = allCandidates.map(ScannerTextParser::stripUtagePrefix)
        }
        val rawCandidates = buildList {
            title?.let(::add)
            addAll(titleCandidates)
        }
        val explicitTitleKanji = rawCandidates
            .firstNotNullOfOrNull { ScannerTextParser.extractKanjiFromTitleCandidates(emptyList(), it) }

        val derivedTotalNotes = when {
            maxDxScore != null && maxDxScore > 0 -> maxDxScore / 3
            else -> maxCombo
        }
        val hasDifficulty = difficulty != null && !isUtage
        val hasLevel = level != null && level in 1.0..15.0
        val hasTotalNotes = derivedTotalNotes != null && derivedTotalNotes > 0
        val hasDxScore = dxScore != null && dxScore > 0
        val hasMaxDxScore = maxDxScore != null && maxDxScore > 0
        val hasKanji = !kanji.isNullOrEmpty()

        var filteredSongs = songs.filter { song ->
            if (song.isDeleted()) return@filter false
            if (isUtage) {
                val utageSheets = song.sheets.filter { it.type.equals("utage", true) }
                if (utageSheets.isEmpty()) return@filter false
                if (explicitTitleKanji != null && !song.hasExplicitUtagePrefix(explicitTitleKanji)) return@filter false
                if (hasKanji && utageSheets.none { it.difficulty.contains(kanji, ignoreCase = true) }) return@filter false
                if (hasTotalNotes) {
                    val totalMatch = utageSheets.any { sheet -> sheet.total?.let { it == derivedTotalNotes } ?: true }
                    if (!totalMatch) {
                        if (hasDxScore) {
                            if (utageSheets.none { sheet -> sheet.total?.let { it * 3 >= dxScore } ?: true }) return@filter false
                        } else {
                            return@filter false
                        }
                    }
                } else if (hasDxScore && utageSheets.none { sheet -> sheet.total?.let { it * 3 >= dxScore } ?: true }) {
                    return@filter false
                }
                true
            } else {
                song.sheets.any { sheet ->
                    if (sheet.type.equals("utage", true)) return@any false
                    if (type != null && !type.equals("utage", true) && !sheet.type.equals(type, true)) return@any false
                    if (difficulty != null && !sheet.difficulty.equals(difficulty, true)) return@any false
                    if (level != null && level in 1.0..15.0) {
                        val sheetLevel = sheet.internalLevelValue ?: sheet.levelValue ?: 0.0
                        if (sheetLevel > 0.0) {
                            if (sheetLevel.toInt() != level.toInt()) return@any false
                        } else if (sheet.level.toIntOrNull() != level.toInt()) {
                            return@any false
                        }
                    }
                    if (derivedTotalNotes != null && derivedTotalNotes > 0 && sheet.total != null && sheet.total != derivedTotalNotes) return@any false
                    if (dxScore != null && dxScore > 0 && sheet.total != null && sheet.total * 3 < dxScore) return@any false
                    if (maxDxScore != null && maxDxScore > 0 && sheet.total != null && sheet.total * 3 != maxDxScore) return@any false
                    true
                }
            }
        }

        val hasAnyValidation = hasDifficulty || hasLevel || hasTotalNotes || hasDxScore || hasMaxDxScore || hasKanji
        if (filteredSongs.isEmpty() && !hasAnyValidation) {
            filteredSongs = songs.filter { !it.isDeleted() }
        }
        if (filteredSongs.size == 1 && hasMaxDxScore) return filteredSongs

        val matchedSongs = mutableListOf<Pair<Song, Int>>()
        val seenIds = mutableSetOf<String>()
        for (candidate in allCandidates) {
            val cleaned = candidate.trim()
            if (cleaned.isEmpty()) continue
            val variants = ScannerTextParser.generateOcrVariants(cleaned, maxVariants = 6)
            for (song in filteredSongs) {
                if (!seenIds.add(song.songIdentifier)) continue

                var matchScore = 0
                var constraintBonus = 0
                val normalizedSongTitle = ScannerTextParser.normalizedSongMatchTitle(song.title)
                if (hasMaxDxScore && song.sheets.any { it.total?.let { total -> total * 3 == maxDxScore } == true }) {
                    constraintBonus += 20
                }
                for (searchCandidate in variants) {
                    val normalizedSearchTitle = ScannerTextParser.normalizedSongMatchTitle(searchCandidate)
                    when {
                        normalizedSongTitle == normalizedSearchTitle -> {
                            matchScore = 110
                            break
                        }
                        song.title.equals(searchCandidate, ignoreCase = true) -> {
                            matchScore = 100
                            break
                        }
                        song.aliases.any { it.equals(searchCandidate, ignoreCase = true) } -> {
                            matchScore = max(matchScore, 95)
                            break
                        }
                        normalizedSongTitle.startsWith(normalizedSearchTitle) && normalizedSongTitle != normalizedSearchTitle -> {
                            matchScore = max(matchScore, if (isUtage) 45 else 80)
                        }
                        normalizedSearchTitle.startsWith(normalizedSongTitle) && normalizedSongTitle != normalizedSearchTitle -> {
                            matchScore = max(matchScore, if (isUtage) 40 else 75)
                        }
                        normalizedSongTitle.contains(normalizedSearchTitle) -> {
                            matchScore = max(matchScore, if (isUtage) 35 else 80)
                        }
                        normalizedSearchTitle.contains(normalizedSongTitle) -> {
                            matchScore = max(matchScore, if (isUtage) 30 else 75)
                        }
                        song.aliases.any { ScannerTextParser.normalizedSongMatchTitle(it).contains(normalizedSearchTitle) } -> {
                            matchScore = max(matchScore, 70)
                        }
                        song.searchKeywords?.contains(searchCandidate, ignoreCase = true) == true -> {
                            matchScore = max(matchScore, 60)
                        }
                        else -> {
                            val dist = ScannerTextParser.levenshteinDistance(cleaned, song.title)
                            val maxLen = max(cleaned.length, song.title.length)
                            if (dist <= max(2, maxLen / 3)) {
                                matchScore = max(matchScore, 50 - dist)
                            } else {
                                for (alias in song.aliases) {
                                    val aliasDist = ScannerTextParser.levenshteinDistance(cleaned, alias)
                                    if (aliasDist <= max(2, max(cleaned.length, alias.length) / 3)) {
                                        matchScore = max(matchScore, 45 - aliasDist)
                                        break
                                    }
                                }
                            }
                        }
                    }
                }

                if (matchScore == 0 && song.title.trim().isEmpty() && (hasMaxDxScore || (hasTotalNotes && hasDifficulty))) {
                    matchScore = 30
                }
                val totalScore = matchScore + constraintBonus
                if (totalScore > 0) {
                    matchedSongs += song to totalScore
                } else {
                    seenIds.remove(song.songIdentifier)
                }
            }
        }

        if (
            matchedSongs.isEmpty() &&
            allCandidates.all { it.trim().isEmpty() } &&
            hasMaxDxScore
        ) {
            filteredSongs.forEach { song ->
                val score = if (song.sheets.any { it.total?.let { total -> total * 3 == maxDxScore } == true }) 50 else 10
                matchedSongs += song to score
            }
        }

        return matchedSongs
            .sortedWith(compareByDescending<Pair<Song, Int>> { it.second }.thenBy { it.first.title.length })
            .map { it.first }
    }

    private fun matchUtageSheet(song: Song, kanji: String?, maxDxScore: Int?, dxScore: Int?): Sheet? {
        val utageSheets = song.sheets.filter { it.type.equals("utage", true) }
        if (utageSheets.isEmpty()) return null

        if (!kanji.isNullOrEmpty()) {
            val kanjiMatches = utageSheets.filter { it.difficulty.contains(kanji, ignoreCase = true) }
            if (kanjiMatches.size == 1) return kanjiMatches.first()
            if (kanjiMatches.size > 1) {
                if (maxDxScore != null && maxDxScore > 0) {
                    val totalNotes = maxDxScore / 3
                    kanjiMatches.firstOrNull { it.total == totalNotes }?.let { return it }
                }
                if (dxScore != null && dxScore > 0) {
                    kanjiMatches.minByOrNull { sheet ->
                        sheet.total?.let { abs(it * 3 - dxScore) } ?: Int.MAX_VALUE
                    }?.let { return it }
                }
                return kanjiMatches.first()
            }
        }

        if (maxDxScore != null && maxDxScore > 0) {
            val totalNotes = maxDxScore / 3
            utageSheets.firstOrNull { it.total == totalNotes }?.let { return it }
        }
        if (dxScore != null && dxScore > 0) {
            val matching = utageSheets.filter { sheet -> sheet.total?.let { it * 3 >= dxScore } ?: true }
            if (matching.size == 1) return matching.first()
            matching.minByOrNull { sheet ->
                sheet.total?.let { abs(it * 3 - dxScore) } ?: Int.MAX_VALUE
            }?.let { return it }
        }
        return utageSheets.firstOrNull()
    }

    private fun Song.isDeleted(): Boolean {
        val standardSheets = sheets.filterNot { it.type.equals("utage", true) }
        return standardSheets.isEmpty() || standardSheets.all { !it.regionJp && !it.regionIntl && !it.regionCn }
    }

    private fun Song.hasExplicitUtagePrefix(kanji: String): Boolean {
        return listOf(title, songIdentifier)
            .any { text ->
                ScannerTextParser.extractKanjiFromTitleCandidates(emptyList(), text) == kanji
            }
    }
}
