import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTypeScript from "eslint-config-next/typescript";

export default defineConfig([
	...nextVitals,
	...nextTypeScript,
	{
		rules: {
			"react-hooks/preserve-manual-memoization": "off",
			"react-hooks/set-state-in-effect": "off",
		},
	},
	globalIgnores([".next/**", "out/**", "node_modules/**", "next-env.d.ts"]),
]);
