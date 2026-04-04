import js from "@eslint/js";
import globals from "globals";
import tseslint from "typescript-eslint";

export default tseslint.config(
	{
		ignores: ["dist/**", "node_modules/**"],
	},
	{
		languageOptions: {
			globals: {
				...globals.node,
			},
		},
	},
	js.configs.recommended,
	...tseslint.configs.recommended,
	{
		files: ["test/**/*.ts", "**/*.spec.ts"],
		languageOptions: {
			globals: {
				...globals.vitest,
			},
		},
		rules: {
			"@typescript-eslint/no-explicit-any": "off",
		},
	},
);
