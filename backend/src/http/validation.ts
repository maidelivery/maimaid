import { sValidator, type Hook } from "@hono/standard-validator";
import type { Context, Env, ValidationTargets } from "hono";
import { z } from "zod";

type ValidationIssue = {
	message: string;
	path?: ReadonlyArray<PropertyKey | { key: PropertyKey }> | undefined;
};

const readIssueField = (issue: ValidationIssue): string | null => {
	const path = issue.path ?? [];
	if (path.length === 0) {
		return null;
	}

	const firstSegment = path[0];
	if (typeof firstSegment === "object" && firstSegment !== null && "key" in firstSegment) {
		return String(firstSegment.key);
	}

	return typeof firstSegment === "string" || typeof firstSegment === "number" ? String(firstSegment) : null;
};

export const buildValidationDetails = <TIssue extends ValidationIssue>(issues: readonly TIssue[]) => {
	const formErrors: string[] = [];
	const fieldErrors: Record<string, string[]> = {};

	for (const issue of issues) {
		const field = readIssueField(issue);
		if (!field) {
			formErrors.push(issue.message);
			continue;
		}

		const existing = fieldErrors[field] ?? [];
		existing.push(issue.message);
		fieldErrors[field] = existing;
	}

	return {
		formErrors,
		fieldErrors,
		issues,
	};
};

export const validationHook: Hook<unknown, Env, string> = (result, c) => {
	if (result.success) {
		return;
	}

	return c.json(
		{
			code: "validation_error",
			message: "Request validation failed.",
			details: buildValidationDetails(result.error),
		},
		400,
	);
};

export const createCustomMethodParamSchema = <const TParamName extends string, TSchema extends z.ZodTypeAny>(
	paramName: TParamName,
	action: string,
	schema: TSchema,
) =>
	z
		.object({
			[paramName]: z.unknown().optional(),
			[`${paramName}:${action}`]: z.unknown().optional(),
		} as Record<string, z.ZodOptional<z.ZodUnknown>>)
		.transform((value, ctx) => {
			const actionSuffix = `:${action}`;
			const rawValue = value[paramName] ?? value[`${paramName}:${action}`];
			const normalizedValue =
				typeof rawValue === "string" && rawValue.endsWith(actionSuffix) ? rawValue.slice(0, -actionSuffix.length) : rawValue;
			const parsed = schema.safeParse(normalizedValue);
			if (!parsed.success) {
				for (const issue of parsed.error.issues) {
					ctx.addIssue({
						...issue,
						path: [paramName, ...issue.path],
					});
				}
				return z.NEVER;
			}

			return {
				[paramName]: parsed.data,
			} as { [K in TParamName]: z.output<TSchema> };
		});

export const standardValidator: typeof sValidator = sValidator;

export type ValidationHook<
	T,
	E extends Env = Env,
	P extends string = string,
	Target extends keyof ValidationTargets = keyof ValidationTargets,
> = Hook<T, E, P, Target>;
export type ValidationContext<E extends Env = Env, P extends string = string> = Context<E, P>;
