import { describe, expect, it } from "vitest";
import {
	buildLetterTokens,
	guessScore,
	maskLetterTokens,
	normalizeLetterCharacter,
	remainingCharacterCount,
	revealCharacter,
	songIndexesMatchingGuess,
} from "../src/services/letter-game.rules.js";

describe("letter game rules", () => {
	it("only preserves half-width spaces in the mask", () => {
		const tokens = buildLetterTokens("A B　!");
		expect(maskLetterTokens(tokens)).toBe("* ***");
		expect(tokens.map((token) => token.visible)).toEqual([false, true, false, false, false]);
		expect(remainingCharacterCount(tokens)).toBe(4);
	});

	it("reveals all matching characters and auto-completes the song", () => {
		const result = revealCharacter("A!A", [], "a");
		expect(result.revealedIndices).toEqual([0, 2]);
		expect(result.newlyRevealedCount).toBe(2);
		expect(result.remainingCharacterCount).toBe(1);
		expect(result.autoCompleted).toBe(false);

		const completed = revealCharacter("A!A", result.revealedIndices, "!");
		expect(completed.autoCompleted).toBe(true);
		expect(maskLetterTokens(completed.tokens)).toBe("A!A");
	});

	it("folds ASCII letter case while preserving other Unicode characters", () => {
		expect(normalizeLetterCharacter("A")).toBe("a");
		expect(normalizeLetterCharacter("ａ")).toBe("a");
		expect(normalizeLetterCharacter("中")).toBe("中");
	});

	it("matches complete titles and aliases using normalized guesses", () => {
		const titleMatch = { title: "Song Title", aliases: [] };
		const aliasMatch = { title: "Other Song", aliases: ["Alias"] };
		expect(songIndexesMatchingGuess("  song title ", [titleMatch, aliasMatch])).toEqual([0]);
		expect(songIndexesMatchingGuess("alias", [titleMatch, aliasMatch])).toEqual([1]);
		expect(songIndexesMatchingGuess("other guess", [titleMatch, aliasMatch])).toEqual([]);
	});

	it("accepts partial titles and aliases only when they identify one song", () => {
		const alpha = { title: "Alpha Song", aliases: ["First Choice"] };
		const beta = { title: "Beta Song", aliases: ["Second Choice"] };
		expect(songIndexesMatchingGuess("lph", [alpha, beta])).toEqual([0]);
		expect(songIndexesMatchingGuess("cond cho", [alpha, beta])).toEqual([1]);
		expect(songIndexesMatchingGuess("song", [alpha, beta])).toEqual([0, 1]);
	});

	it("prioritizes a complete match over partial matches in other songs", () => {
		const exactAlias = { title: "Alpha", aliases: ["A"] };
		const partialTitle = { title: "Beta", aliases: [] };
		expect(songIndexesMatchingGuess("a", [exactAlias, partialTitle])).toEqual([0]);
	});

	it("uses the distinct normal and blind guess scores", () => {
		expect(guessScore(4, false)).toBe(9);
		expect(guessScore(4, true)).toBe(14);
	});
});
