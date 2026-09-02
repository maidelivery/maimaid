export type LetterToken = {
	value: string;
	normalized: string;
	visible: boolean;
};

export type LetterRevealResult = {
	tokens: LetterToken[];
	revealedIndices: number[];
	newlyRevealedCount: number;
	remainingCharacterCount: number;
	autoCompleted: boolean;
};

export type SongGuessCandidate = {
	title: string;
	aliases: readonly string[];
};

const segmenter = new Intl.Segmenter(undefined, { granularity: "grapheme" });

/**
 * Normalize one user-visible character. ASCII letters are folded for the
 * game's case-insensitive matching rule; every other grapheme remains exact.
 */
export const normalizeLetterCharacter = (value: string): string => {
	const normalized = value.normalize("NFKC");
	return /^[A-Za-z]$/u.test(normalized) ? normalized.toLowerCase() : normalized;
};

export const normalizeGuess = (value: string): string =>
	value
		.normalize("NFKC")
		.trim()
		.replaceAll(/[A-Z]/gu, (character) => character.toLowerCase());

export const segmentLetterText = (value: string): string[] => Array.from(segmenter.segment(value), ({ segment }) => segment);

export const buildLetterTokens = (title: string, revealedIndices: Iterable<number> = []): LetterToken[] => {
	const revealed = new Set(revealedIndices);
	return segmentLetterText(title).map((value, index) => ({
		value,
		normalized: normalizeLetterCharacter(value),
		visible: value === " " || revealed.has(index),
	}));
};

export const maskLetterTokens = (tokens: LetterToken[]): string =>
	tokens.map((token) => (token.visible ? token.value : "*")).join("");

export const remainingCharacterCount = (tokens: LetterToken[]): number =>
	tokens.reduce((count, token) => count + (token.value === " " || token.visible ? 0 : 1), 0);

export const revealCharacter = (
	title: string,
	revealedIndices: Iterable<number>,
	requestedCharacter: string,
): LetterRevealResult => {
	const requested = segmentLetterText(requestedCharacter)[0];
	if (!requested) {
		throw new Error("A character is required.");
	}

	const tokens = buildLetterTokens(title, revealedIndices);
	const nextIndices = new Set(revealedIndices);
	const normalizedRequested = normalizeLetterCharacter(requested);
	for (const [index, token] of tokens.entries()) {
		if (token.value !== " " && !token.visible && token.normalized === normalizedRequested) {
			nextIndices.add(index);
		}
	}

	const nextTokens = buildLetterTokens(title, nextIndices);
	const newlyRevealedCount = [...nextIndices].filter((index) => !new Set(revealedIndices).has(index)).length;
	const remaining = remainingCharacterCount(nextTokens);
	return {
		tokens: nextTokens,
		revealedIndices: [...nextIndices].sort((left, right) => left - right),
		newlyRevealedCount,
		remainingCharacterCount: remaining,
		autoCompleted: remaining === 0,
	};
};

export const songIndexesMatchingGuess = (guess: string, songs: readonly SongGuessCandidate[]): number[] => {
	const normalizedGuess = normalizeGuess(guess);
	if (!normalizedGuess) return [];

	const normalizedCandidates = songs.map((song, index) => ({
		index,
		values: [song.title, ...song.aliases].map(normalizeGuess),
	}));
	const exactMatches = normalizedCandidates
		.filter(({ values }) => values.some((candidate) => candidate === normalizedGuess))
		.map(({ index }) => index);
	if (exactMatches.length > 0) return exactMatches;

	return normalizedCandidates
		.filter(({ values }) => values.some((candidate) => candidate.includes(normalizedGuess)))
		.map(({ index }) => index);
};

export const guessScore = (remainingCharacters: number, blind: boolean): number =>
	(blind ? 10 : 5) + Math.max(0, remainingCharacters);
