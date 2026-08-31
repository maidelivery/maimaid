import { describe, expect, it } from "vitest";
import {
	buildLetterTokens,
	guessScore,
	guessSongMatches,
	maskLetterTokens,
	normalizeLetterCharacter,
	remainingCharacterCount,
	revealCharacter,
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

	it("matches titles and aliases using normalized guesses", () => {
		expect(guessSongMatches("  song title ", "Song Title", [])).toBe(true);
		expect(guessSongMatches("alias", "Song Title", ["Alias"])).toBe(true);
		expect(guessSongMatches("other", "Song Title", ["Alias"])).toBe(false);
	});

	it("uses the distinct normal and blind guess scores", () => {
		expect(guessScore(4, false)).toBe(9);
		expect(guessScore(4, true)).toBe(14);
	});
});
