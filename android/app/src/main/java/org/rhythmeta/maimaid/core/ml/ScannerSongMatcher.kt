package org.rhythmeta.maimaid.core.ml

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

object ScannerSongMatcher {
    private val UtagePrefix = Regex("^(?:【([^】]+)】|\\[([^]]+)]|［([^］]+)］)\\s*")
    private val OcrSubstitutions = mapOf(
        'O' to listOf('0', 'D', 'Q'), '0' to listOf('O', 'D'), 'D' to listOf('O', '0'),
        'I' to listOf('1', 'l', 'L'), '1' to listOf('I', 'l'), 'l' to listOf('I', '1'),
        'L' to listOf('I', '1'), 'S' to listOf('5', 'Z'), '5' to listOf('S'),
        'Z' to listOf('S', '2'), '2' to listOf('Z'), 'B' to listOf('8'), '8' to listOf('B'),
        'G' to listOf('6', 'C'), '6' to listOf('G'), 'C' to listOf('G'), 'Q' to listOf('O', '0'),
        '職' to listOf('蔵', '藏', '概'), '蔵' to listOf('職', '藏'), '藏' to listOf('職', '蔵'),
        '黒' to listOf('黑'), '黑' to listOf('黒'), '響' to listOf('郷'), '郷' to listOf('響'),
        '桜' to listOf('櫻'), '櫻' to listOf('桜'), '竜' to listOf('龍'), '龍' to listOf('竜'),
        '國' to listOf('国'), '国' to listOf('國'), '円' to listOf('圓'), '圓' to listOf('円'),
        '変' to listOf('變'), '變' to listOf('変'), '戦' to listOf('戰'), '戰' to listOf('戦'),
        '関' to listOf('關'), '關' to listOf('関'), '広' to listOf('廣'), '廣' to listOf('広'),
        '駅' to listOf('驛'), '驛' to listOf('駅'), '帯' to listOf('帶'), '帶' to listOf('帯'),
        '極' to listOf('极'), '极' to listOf('極'), '転' to listOf('轉'), '轉' to listOf('転'),
        '画' to listOf('畫'), '畫' to listOf('画'),
    )

    fun match(recognition: ScannerRawResult, catalog: ScannerCatalog): List<ScannerMatch> {
        val chooseScan = recognition.screenType == MaimaiScreenType.Choose
        val rawCandidates = listOfNotNull(recognition.title) + recognition.titleCandidates
        var candidates = recognition.titleCandidates.toMutableList()
        recognition.title?.let { candidates.add(0, it) }
        val isUtage = recognition.chartType.equals("utage", ignoreCase = true)
        if (isUtage) candidates = candidates.mapTo(mutableListOf(), ::stripUtagePrefix)
        val explicitTitleKanji = rawCandidates.firstNotNullOfOrNull(::extractUtagePrefixKanji)
        val derivedTotalNotes = recognition.maxDxScore?.takeIf { it > 0 }?.div(3)
            ?: recognition.maxCombo?.takeIf { it > 0 }
        val validatedLevel = recognition.level?.takeIf { it in 1.0..15.0 }
        val validatedDxScore = recognition.dxScore?.takeIf { it > 0 }
        val validatedMaxDxScore = recognition.maxDxScore?.takeIf { it > 0 }
        val validatedKanji = recognition.kanji?.takeIf(String::isNotEmpty)
        val hasAnyValidation = (!isUtage && recognition.difficulty != null) ||
            validatedLevel != null || derivedTotalNotes != null || validatedDxScore != null ||
            validatedMaxDxScore != null || validatedKanji != null

        var filteredSongs = catalog.songs.filter { song ->
            if (chooseScan && isBlankTitleSong(song, catalog)) return@filter false
            (isUtage || hasAvailableStandardSheets(song, catalog)) && sheetsFor(song, catalog).any { sheet ->
                sheetMatches(
                    sheet = sheet,
                    song = song,
                    recognition = recognition,
                    isUtage = isUtage,
                    explicitTitleKanji = explicitTitleKanji,
                    derivedTotalNotes = derivedTotalNotes,
                    validatedLevel = validatedLevel,
                    validatedDxScore = validatedDxScore,
                    validatedMaxDxScore = validatedMaxDxScore,
                    validatedKanji = validatedKanji,
                )
            }
        }
        filteredSongs = recoverUtageSongs(
            strictSongs = filteredSongs,
            recognition = recognition,
            candidates = candidates,
            explicitTitleKanji = explicitTitleKanji,
            catalog = catalog,
        )
        if (filteredSongs.isEmpty() && !hasAnyValidation) {
            filteredSongs = catalog.songs.filter {
                (!chooseScan || !isBlankTitleSong(it, catalog)) &&
                    hasAvailableStandardSheets(it, catalog)
            }
        }
        // The detector can confuse the standard chart badge with the DX badge.
        // Keep the numeric constraints, but let a reliable title recover a song
        // that was removed solely by that chart-type classification.
        if (
            !isUtage &&
            !recognition.chartType.isNullOrBlank() &&
            candidates.any { it.isNotBlank() } &&
            filteredSongs.none { song ->
                !isBlankTitleSong(song, catalog) &&
                    hasTitleEvidence(candidates, song, catalog.aliasesBySong[song.songIdentifier].orEmpty())
            }
        ) {
            val relaxedRecognition = recognition.copy(chartType = null)
            val relaxedSongs = catalog.songs.filter { song ->
                if (chooseScan && isBlankTitleSong(song, catalog)) return@filter false
                hasAvailableStandardSheets(song, catalog) && sheetsFor(song, catalog).any { sheet ->
                    sheetMatches(
                        sheet = sheet,
                        song = song,
                        recognition = relaxedRecognition,
                        isUtage = false,
                        explicitTitleKanji = null,
                        derivedTotalNotes = derivedTotalNotes,
                        validatedLevel = validatedLevel,
                        validatedDxScore = validatedDxScore,
                        validatedMaxDxScore = validatedMaxDxScore,
                        validatedKanji = validatedKanji,
                    )
                }
            }
            if (relaxedSongs.any { song ->
                    !isBlankTitleSong(song, catalog) &&
                        hasTitleEvidence(candidates, song, catalog.aliasesBySong[song.songIdentifier].orEmpty())
                }) {
                filteredSongs = relaxedSongs
            }
        }
        if (filteredSongs.size == 1 && validatedMaxDxScore != null) {
            val song = filteredSongs.single()
            return listOf(ScannerMatch(song, resolveSheet(song, recognition, catalog), recognition))
        }

        val scored = mutableMapOf<String, Pair<SongEntity, Int>>()
        candidates.forEach { candidate ->
            val cleaned = candidate.trim()
            if (cleaned.isEmpty()) return@forEach
            val variants = generateOcrVariants(cleaned, 6)
            filteredSongs.forEach { song ->
                val aliases = catalog.aliasesBySong[song.songIdentifier].orEmpty()
                val blankTitleSong = isBlankTitleSong(song, catalog)
                var score = 0
                val normalizedSongTitle = normalizeMatchTitle(song.title)
                if (!blankTitleSong) {
                    variants.forEach { searchCandidate ->
                        val normalizedSearch = normalizeMatchTitle(searchCandidate)
                        score = maxOf(score, when {
                            normalizedSongTitle == normalizedSearch -> 110
                            song.title.equals(searchCandidate, ignoreCase = true) -> 100
                            aliases.any { it.equals(searchCandidate, ignoreCase = true) } -> 95
                            normalizedSongTitle.startsWith(normalizedSearch) && normalizedSongTitle != normalizedSearch -> if (isUtage) 45 else 80
                            normalizedSearch.startsWith(normalizedSongTitle) && normalizedSongTitle != normalizedSearch -> if (isUtage) 40 else 75
                            song.title.contains(searchCandidate, ignoreCase = true) -> if (isUtage) 35 else 80
                            searchCandidate.contains(song.title, ignoreCase = true) -> if (isUtage) 30 else 75
                            aliases.any { it.contains(searchCandidate, ignoreCase = true) } -> 70
                            else -> {
                                val titleDistance = levenshtein(cleaned, song.title)
                                if (titleDistance <= maxOf(2, maxOf(cleaned.length, song.title.length) / 3)) {
                                    50 - titleDistance
                                } else {
                                    aliases.maxOfOrNull { alias ->
                                        val aliasDistance = levenshtein(cleaned, alias)
                                        (45 - aliasDistance).takeIf {
                                            aliasDistance <= maxOf(2, maxOf(cleaned.length, alias.length) / 3)
                                        } ?: 0
                                    } ?: 0
                                }
                            }
                        })
                    }
                }

                if (score == 0 && blankTitleSong && (validatedMaxDxScore != null ||
                        (derivedTotalNotes != null && recognition.difficulty != null))) {
                    score = 30
                }
                val constraintBonus = if (
                    validatedMaxDxScore != null && sheetsFor(song, catalog).any {
                        it.total?.times(3) == validatedMaxDxScore
                    }
                ) 20 else 0
                val hasTitleCandidate = candidates.any(String::isNotBlank)
                if (score > 0 || (!hasTitleCandidate && constraintBonus > 0)) {
                    val totalScore = score + constraintBonus
                    val previous = scored[song.songIdentifier]
                    if (previous == null || totalScore > previous.second) {
                        scored[song.songIdentifier] = song to totalScore
                    }
                }
            }
        }
        if (scored.isEmpty() && candidates.all(String::isBlank) && validatedMaxDxScore != null) {
            val exactSongs = filteredSongs.filter { song ->
                sheetsFor(song, catalog).any { sheet ->
                    sheet.total?.times(3) == validatedMaxDxScore && sheetMatches(
                        sheet = sheet,
                        song = song,
                        recognition = recognition,
                        isUtage = isUtage,
                        explicitTitleKanji = explicitTitleKanji,
                        derivedTotalNotes = derivedTotalNotes,
                        validatedLevel = validatedLevel,
                        validatedDxScore = validatedDxScore,
                        validatedMaxDxScore = validatedMaxDxScore,
                        validatedKanji = validatedKanji,
                    )
                }
            }
            exactSongs.singleOrNull()?.let { song -> scored[song.songIdentifier] = song to 50 }
        }
        return scored.values
            .sortedWith(compareByDescending<Pair<SongEntity, Int>> { it.second }.thenBy { it.first.title.length })
            .map { (song) -> ScannerMatch(song, resolveSheet(song, recognition, catalog), recognition) }
    }

    fun matchFast(recognition: ScannerRawResult, catalog: ScannerCatalog): List<ScannerMatch> {
        val chooseScan = recognition.screenType == MaimaiScreenType.Choose
        val rawCandidates = listOfNotNull(recognition.title) + recognition.titleCandidates
        val candidates = if (recognition.chartType.equals("utage", ignoreCase = true)) {
            rawCandidates.map(::stripUtagePrefix)
        } else {
            rawCandidates
        }
        val isUtage = recognition.chartType.equals("utage", ignoreCase = true)
        val explicitTitleKanji = rawCandidates.firstNotNullOfOrNull(::extractUtagePrefixKanji)
        val derivedTotalNotes = recognition.maxDxScore?.takeIf { it > 0 }?.div(3)
            ?: recognition.maxCombo?.takeIf { it > 0 }
        fun validSheet(sheet: SheetEntity, song: SongEntity): Boolean = sheetMatches(
            sheet = sheet,
            song = song,
            recognition = recognition,
            isUtage = isUtage,
            explicitTitleKanji = explicitTitleKanji,
            derivedTotalNotes = derivedTotalNotes,
            validatedLevel = recognition.level?.takeIf { it in 1.0..15.0 },
            validatedDxScore = recognition.dxScore?.takeIf { it > 0 },
            validatedMaxDxScore = recognition.maxDxScore?.takeIf { it > 0 },
            validatedKanji = recognition.kanji?.takeIf(String::isNotEmpty),
        )
        val eligibleSongs = catalog.songs.filter { song ->
            if (chooseScan && isBlankTitleSong(song, catalog)) return@filter false
            catalog.sheetsBySong[song.songIdentifier].orEmpty().any { validSheet(it, song) }
        }
        var effectiveEligibleSongs = recoverUtageSongs(
            strictSongs = eligibleSongs,
            recognition = recognition,
            candidates = candidates,
            explicitTitleKanji = explicitTitleKanji,
            catalog = catalog,
        )
        if (
            !isUtage &&
            !recognition.chartType.isNullOrBlank() &&
            candidates.any { it.isNotBlank() } &&
            eligibleSongs.none { song ->
                !isBlankTitleSong(song, catalog) &&
                    hasTitleEvidence(candidates, song, catalog.aliasesBySong[song.songIdentifier].orEmpty())
            }
        ) {
            val relaxedRecognition = recognition.copy(chartType = null)
            val relaxedSongs = catalog.songs.filter { song ->
                if (chooseScan && isBlankTitleSong(song, catalog)) return@filter false
                sheetsFor(song, catalog).any { sheet ->
                    sheetMatches(
                        sheet = sheet,
                        song = song,
                        recognition = relaxedRecognition,
                        isUtage = false,
                        explicitTitleKanji = null,
                        derivedTotalNotes = derivedTotalNotes,
                        validatedLevel = recognition.level?.takeIf { it in 1.0..15.0 },
                        validatedDxScore = recognition.dxScore?.takeIf { it > 0 },
                        validatedMaxDxScore = recognition.maxDxScore?.takeIf { it > 0 },
                        validatedKanji = recognition.kanji?.takeIf(String::isNotEmpty),
                    )
                }
            }
            if (relaxedSongs.any { song ->
                    !isBlankTitleSong(song, catalog) &&
                        hasTitleEvidence(candidates, song, catalog.aliasesBySong[song.songIdentifier].orEmpty())
                }) {
                effectiveEligibleSongs = relaxedSongs
            }
        }
        val exact = mutableListOf<SongEntity>()
        val partial = mutableListOf<SongEntity>()
            candidates.forEach { candidate ->
            val cleaned = candidate.trim()
            if (cleaned.length < 2) return@forEach
            val normalized = normalizeMatchTitle(cleaned)
                effectiveEligibleSongs.forEach { song ->
                    val normalizedTitle = normalizeMatchTitle(song.title)
                    if (normalizedTitle.isEmpty()) return@forEach
                    when {
                    normalizedTitle == normalized -> exact += song
                    (!isUtage && (song.title.contains(cleaned, true) || cleaned.contains(song.title, true))) ||
                        (isUtage && normalizedTitle.startsWith(normalized)) -> partial += song
                }
            }
            if (exact.isNotEmpty() || partial.isNotEmpty()) return@forEach
            if (cleaned.length > 4) {
                effectiveEligibleSongs.forEach { song ->
                    if (fuzzyMatch(cleaned, song.title)) partial += song
                }
            }
        }
        val selected = (exact + partial).distinctBy(SongEntity::songIdentifier).take(4)
        val uniqueUtageKanjiSong = if (isUtage && !recognition.kanji.isNullOrBlank()) {
            songsMatchingUtageTextConstraints(
                recognition = recognition,
                explicitTitleKanji = explicitTitleKanji,
                catalog = catalog,
            ).filter { song ->
                sheetsFor(song, catalog).any { sheet ->
                    hasNoisyUtageKanjiEvidence(recognition.kanji, sheet.difficulty)
                }
            }.singleOrNull()
        } else {
            null
        }
        val fallback = if (selected.isNotEmpty()) {
            selected
        } else if (uniqueUtageKanjiSong != null) {
            listOf(uniqueUtageKanjiSong)
        } else if (recognition.maxDxScore?.let { it > 0 } == true) {
            val maxDxScore = requireNotNull(recognition.maxDxScore)
            effectiveEligibleSongs.filter { song ->
                sheetsFor(song, catalog).any { sheet ->
                    sheet.total?.times(3) == maxDxScore && validSheet(sheet, song)
                }
            }.singleOrNull()?.let(::listOf).orEmpty()
        } else {
            emptyList()
        }
        return fallback.map { song ->
            ScannerMatch(song, resolveSheet(song, recognition, catalog), recognition)
        }
    }

    fun resolveSheet(
        song: SongEntity,
        recognition: ScannerRawResult,
        catalog: ScannerCatalog,
    ): SheetEntity? {
        val sheets = sheetsFor(song, catalog)
        if (recognition.chartType.equals("utage", ignoreCase = true)) {
            val strict = matchUtageSheet(sheets, recognition.kanji, recognition.maxDxScore, recognition.dxScore)
            if (strict != null) return strict

            val titleCandidates = listOfNotNull(recognition.title) + recognition.titleCandidates
            val aliases = catalog.aliasesBySong[song.songIdentifier].orEmpty()
            val hasTitleEvidence = hasTitleEvidence(
                candidates = titleCandidates,
                song = song,
                aliases = aliases,
            )
            val relaxed = sheets.filter { sheet ->
                sheet.type.equals("utage", ignoreCase = true) &&
                    utageKanjiMatches(recognition.kanji, sheet.difficulty)
            }
            val relaxedSheet = relaxed.singleOrNull() ?: return null
            return relaxedSheet.takeIf {
                hasTitleEvidence || hasNoisyUtageKanjiEvidence(recognition.kanji, it.difficulty)
            }
        }
        val strictCandidates = sheets.filter { sheet ->
            ScannerNoteCountValidator.isCompatible(recognition.maxDxScore, sheet.total) &&
                !sheet.type.equals("utage", ignoreCase = true) &&
                (recognition.chartType.isNullOrBlank() || sheet.type.equals(recognition.chartType, ignoreCase = true)) &&
                (recognition.difficulty.isNullOrBlank() || sheet.difficulty.equals(recognition.difficulty, ignoreCase = true))
        }
        val titleCandidates = listOfNotNull(recognition.title) + recognition.titleCandidates
        val canIgnoreChartType = recognition.chartType.isNullOrBlank() ||
            hasTitleEvidence(
                candidates = titleCandidates,
                song = song,
                aliases = catalog.aliasesBySong[song.songIdentifier].orEmpty(),
            )
        val candidates = if (strictCandidates.isNotEmpty() || !canIgnoreChartType) {
            strictCandidates
        } else {
            // A detector type error must not discard the chart after the song
            // title has already identified it.
            sheets.filter { sheet ->
                ScannerNoteCountValidator.isCompatible(recognition.maxDxScore, sheet.total) &&
                    !sheet.type.equals("utage", ignoreCase = true) &&
                    (recognition.difficulty.isNullOrBlank() || sheet.difficulty.equals(recognition.difficulty, ignoreCase = true))
            }
        }
        if (candidates.size <= 1) return candidates.firstOrNull()
        recognition.maxDxScore?.takeIf { it > 0 }?.let { maxDx ->
            candidates.firstOrNull { it.total == maxDx / 3 }?.let { return it }
        }
        recognition.dxScore?.takeIf { it > 0 }?.let { dxScore ->
            val possible = candidates.filter { it.total == null || it.total * 3 >= dxScore }
            if (possible.size == 1) return possible.first()
            possible.minByOrNull { sheet -> sheet.total?.let { abs(it * 3 - dxScore) } ?: Int.MAX_VALUE }
                ?.let { return it }
        }
        return candidates.first()
    }

    private fun sheetMatches(
        sheet: SheetEntity,
        song: SongEntity,
        recognition: ScannerRawResult,
        isUtage: Boolean,
        explicitTitleKanji: String?,
        derivedTotalNotes: Int?,
        validatedLevel: Double?,
        validatedDxScore: Int?,
        validatedMaxDxScore: Int?,
        validatedKanji: String?,
    ): Boolean {
        if (!ScannerNoteCountValidator.isCompatible(validatedMaxDxScore, sheet.total)) return false
        if (isUtage) {
            if (!sheet.type.equals("utage", ignoreCase = true)) return false
            if (!utageKanjiMatches(validatedKanji, sheet.difficulty)) return false
            if (explicitTitleKanji != null && !songHasExplicitUtagePrefix(song, explicitTitleKanji)) return false
            if (derivedTotalNotes != null && sheet.total != null && sheet.total != derivedTotalNotes) return false
            if (validatedDxScore != null && sheet.total != null && sheet.total * 3 < validatedDxScore) return false
            return true
        }
        if (sheet.type.equals("utage", ignoreCase = true)) return false
        if (!recognition.chartType.isNullOrBlank() && !sheet.type.equals(recognition.chartType, ignoreCase = true)) return false
        if (recognition.difficulty != null && !sheet.difficulty.equals(recognition.difficulty, ignoreCase = true)) return false
        if (validatedLevel != null) {
            val sheetLevel = sheet.internalLevelValue ?: sheet.levelValue
            if (sheetLevel != null && sheetLevel > 0 && sheetLevel.toInt() != validatedLevel.toInt()) return false
            if (sheetLevel == null && sheet.level.toDoubleOrNull()?.toInt() != validatedLevel.toInt()) return false
        }
        if (derivedTotalNotes != null && sheet.total != null && sheet.total != derivedTotalNotes) return false
        if (validatedDxScore != null && sheet.total != null && sheet.total * 3 < validatedDxScore) return false
        if (validatedMaxDxScore != null && sheet.total != null && sheet.total * 3 != validatedMaxDxScore) return false
        return true
    }

    private fun matchUtageSheet(
        sheets: List<SheetEntity>,
        kanji: String?,
        maxDxScore: Int?,
        dxScore: Int?,
    ): SheetEntity? {
        val utageSheets = sheets.filter {
            it.type.equals("utage", ignoreCase = true) &&
                ScannerNoteCountValidator.isCompatible(maxDxScore, it.total)
        }
        if (utageSheets.isEmpty()) return null
        if (!kanji.isNullOrEmpty()) {
            val kanjiMatches = utageSheets.filter { utageKanjiMatches(kanji, it.difficulty) }
            if (kanjiMatches.size == 1) return kanjiMatches.first()
            if (kanjiMatches.size > 1) return chooseByScore(kanjiMatches, maxDxScore, dxScore)
        }
        return chooseByScore(utageSheets, maxDxScore, dxScore)
    }

    private fun chooseByScore(
        sheets: List<SheetEntity>,
        maxDxScore: Int?,
        dxScore: Int?,
    ): SheetEntity? {
        maxDxScore?.takeIf { it > 0 }?.let { maxDx ->
            sheets.firstOrNull { it.total == maxDx / 3 }?.let { return it }
        }
        dxScore?.takeIf { it > 0 }?.let { dx ->
            val possible = sheets.filter { it.total == null || it.total * 3 >= dx }
            possible.minByOrNull { it.total?.let { total -> abs(total * 3 - dx) } ?: Int.MAX_VALUE }
                ?.let { return it }
        }
        return sheets.firstOrNull()
    }

    private fun hasAvailableStandardSheets(song: SongEntity, catalog: ScannerCatalog): Boolean {
        val standard = sheetsFor(song, catalog).filterNot { it.type.equals("utage", ignoreCase = true) }
        return standard.isNotEmpty() && standard.any { it.regionJp || it.regionIntl || it.regionCn }
    }

    private fun sheetsFor(song: SongEntity, catalog: ScannerCatalog): List<SheetEntity> =
        catalog.sheetsBySong[song.songIdentifier].orEmpty()

    private fun isBlankTitleSong(song: SongEntity, catalog: ScannerCatalog): Boolean =
        song.title.isBlank() || sheetsFor(song, catalog).any { it.providerSongId == BlankTitleProviderSongId }

    private fun stripUtagePrefix(title: String): String = Normalizer.normalize(
        title.replaceFirst(UtagePrefix, "").trim(),
        Normalizer.Form.NFKC,
    )

    private fun extractUtagePrefixKanji(text: String): String? {
        val match = UtagePrefix.find(text.trim()) ?: return null
        return match.groupValues.drop(1).firstOrNull(String::isNotEmpty)?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun songHasExplicitUtagePrefix(song: SongEntity, kanji: String?): Boolean =
        listOf(song.title, song.songIdentifier).any { extractUtagePrefixKanji(it) == kanji }

    private fun recoverUtageSongs(
        strictSongs: List<SongEntity>,
        recognition: ScannerRawResult,
        candidates: List<String>,
        explicitTitleKanji: String?,
        catalog: ScannerCatalog,
    ): List<SongEntity> {
        if (!recognition.chartType.equals("utage", ignoreCase = true)) return strictSongs
        if (strictSongs.any { song ->
                hasTitleEvidence(candidates, song, catalog.aliasesBySong[song.songIdentifier].orEmpty())
            }
        ) {
            return strictSongs
        }

        val relaxedSongs = songsMatchingUtageTextConstraints(recognition, explicitTitleKanji, catalog)
        val titleMatches = relaxedSongs.filter { song ->
            val aliases = catalog.aliasesBySong[song.songIdentifier].orEmpty()
            hasTitleEvidence(candidates, song, aliases)
        }
        if (titleMatches.isNotEmpty()) return titleMatches
        val noisyKanjiMatches = relaxedSongs.filter { song ->
            sheetsFor(song, catalog).any { sheet ->
                hasNoisyUtageKanjiEvidence(recognition.kanji, sheet.difficulty)
            }
        }
        return if (noisyKanjiMatches.size == 1) noisyKanjiMatches else strictSongs
    }

    private fun songsMatchingUtageTextConstraints(
        recognition: ScannerRawResult,
        explicitTitleKanji: String?,
        catalog: ScannerCatalog,
    ): List<SongEntity> = catalog.songs.filter { song ->
        if (explicitTitleKanji != null && !songHasExplicitUtagePrefix(song, explicitTitleKanji)) {
            return@filter false
        }
        sheetsFor(song, catalog).any { sheet ->
            sheet.type.equals("utage", ignoreCase = true) &&
                utageKanjiMatches(recognition.kanji, sheet.difficulty)
        }
    }

    private fun utageKanjiMatches(recognized: String?, difficulty: String): Boolean {
        if (recognized.isNullOrBlank()) return true
        val expected = extractUtagePrefixKanji(difficulty) ?: return difficulty.contains(recognized)
        val normalizedRecognized = normalizeUtageKanji(recognized)
        return normalizedRecognized == expected || normalizedRecognized.contains(expected)
    }

    private fun hasNoisyUtageKanjiEvidence(recognized: String?, difficulty: String): Boolean {
        if (recognized.isNullOrBlank()) return false
        val expected = extractUtagePrefixKanji(difficulty) ?: return false
        val normalizedRecognized = normalizeUtageKanji(recognized)
        return normalizedRecognized != expected && normalizedRecognized.contains(expected)
    }

    private fun normalizeUtageKanji(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replace(Regex("[\\s【】\\[\\]［］]+"), "")

    private fun normalizeMatchTitle(title: String): String = Normalizer.normalize(
        stripUtagePrefix(title).replace('\u3000', ' '),
        Normalizer.Form.NFKD,
    ).replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase(Locale.ROOT)

    private fun hasTitleEvidence(
        candidates: List<String>,
        song: SongEntity,
        aliases: List<String>,
    ): Boolean {
        if (song.title.isBlank()) return false
        val normalizedSongTitle = normalizeMatchTitle(song.title)
        return candidates.any { candidate ->
            val cleaned = candidate.trim()
            if (cleaned.isEmpty()) {
                false
            } else {
                generateOcrVariants(cleaned, 6).any { searchCandidate ->
                    val normalizedSearch = normalizeMatchTitle(searchCandidate)
                    val exact = normalizedSongTitle == normalizedSearch ||
                        song.title.equals(searchCandidate, ignoreCase = true) ||
                        aliases.any { it.equals(searchCandidate, ignoreCase = true) }
                    val partial = cleaned.length >= 2 && (
                        normalizedSongTitle.startsWith(normalizedSearch) ||
                            normalizedSearch.startsWith(normalizedSongTitle) ||
                            song.title.contains(searchCandidate, ignoreCase = true) ||
                            searchCandidate.contains(song.title, ignoreCase = true) ||
                            aliases.any { it.contains(searchCandidate, ignoreCase = true) }
                        )
                    val fuzzy = cleaned.length > 4 && (
                        fuzzyMatch(cleaned, song.title) ||
                            aliases.any { fuzzyMatch(cleaned, it) }
                        )
                    exact || partial || fuzzy
                }
            }
        }
    }

    private fun generateOcrVariants(text: String, maxVariants: Int): List<String> {
        val variants = mutableListOf(text)
        val queue = ArrayDeque<String>().apply { add(text) }
        val seen = mutableSetOf(text)
        while (queue.isNotEmpty() && variants.size < maxVariants) {
            val current = queue.removeFirst()
            current.forEachIndexed { index, character ->
                OcrSubstitutions[character].orEmpty().forEach { replacement ->
                    if (variants.size >= maxVariants) return@forEach
                    val variant = current.replaceRange(index, index + 1, replacement.toString())
                    if (seen.add(variant)) {
                        variants += variant
                        queue += variant
                    }
                }
            }
        }
        return variants
    }

    private fun levenshtein(first: String, second: String): Int {
        val a = first.lowercase(Locale.ROOT)
        val b = second.lowercase(Locale.ROOT)
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (a[i] == b[j]) 0 else 1,
                )
            }
            previous = current
        }
        return previous[b.length]
    }

    private fun fuzzyMatch(first: String, second: String): Boolean {
        val left = first.lowercase(Locale.ROOT).filterNot(Char::isWhitespace)
        val right = second.lowercase(Locale.ROOT).filterNot(Char::isWhitespace)
        if (abs(left.length - right.length) > 2) return false
        return levenshtein(left, right) <= maxOf(1, left.length / 4)
    }

    private const val BlankTitleProviderSongId = 11_422
}
