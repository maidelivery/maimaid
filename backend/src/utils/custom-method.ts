import type { Context } from "hono";
import type { AppEnv } from "../types/hono.js";

export const readCustomMethodParam = (
	c: Context<AppEnv>,
	paramName: string,
	action: string,
) => {
	const directValue = c.req.param(paramName);
	if (directValue) {
		return directValue;
	}

	const rawParamName = `${paramName}:${action}`;
	const rawValue = c.req.param(rawParamName);
	if (!rawValue) {
		return "";
	}

	const actionSuffix = `:${action}`;
	return rawValue.endsWith(actionSuffix) ? rawValue.slice(0, -actionSuffix.length) : rawValue;
};
