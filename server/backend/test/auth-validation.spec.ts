import { describe, expect, it } from "vitest";
import { isPasswordComplexEnough } from "../src/lib/auth-validation.js";

describe("auth password validation", () => {
  it("accepts passwords matching the complexity rule", () => {
    expect(isPasswordComplexEnough("Abcd1234!")).toBe(true);
    expect(isPasswordComplexEnough("XyZ_9988##")).toBe(true);
  });

  it("rejects passwords that do not match the complexity rule", () => {
    expect(isPasswordComplexEnough("short1!")).toBe(false);
    expect(isPasswordComplexEnough("alllowercase123!")).toBe(false);
    expect(isPasswordComplexEnough("ALLUPPERCASE123!")).toBe(false);
    expect(isPasswordComplexEnough("NoDigits!!")).toBe(false);
    expect(isPasswordComplexEnough("NoSymbols1234")).toBe(false);
  });
});

