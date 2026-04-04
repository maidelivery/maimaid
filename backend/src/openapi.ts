import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import type { Hono } from "hono";
import ts from "typescript";
import type { Env } from "./env.js";
import type { AppEnv } from "./types/hono.js";

const DOCUMENTED_METHODS = new Set(["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"]);
const METHOD_CALLS = new Set(["get", "post", "put", "patch", "delete", "options", "head"]);

type RouteEntry = {
	method: string;
	path: string;
	handler?: unknown;
};

type OpenApiSchema = Record<string, unknown>;

type OpenApiParameter = {
	name: string;
	in: "path" | "query";
	required: boolean;
	schema: OpenApiSchema;
};

type OpenApiPathItem = Record<string, unknown>;

type OpenApiDocument = {
	openapi: string;
	info: {
		title: string;
		description: string;
		version: string;
	};
	servers: Array<{
		url: string;
		description: string;
	}>;
	tags: Array<{
		name: string;
	}>;
	paths: Record<string, OpenApiPathItem>;
};

type TypedOperation = {
	parameters?: OpenApiParameter[];
	requestBody?: Record<string, unknown>;
	responses?: Record<string, unknown>;
};

const normalizePath = (routePath: string) => routePath.replaceAll(/:([A-Za-z0-9_]+)/g, "{$1}");

const joinRoutePath = (prefix: string, child: string) => {
	const normalizedPrefix = prefix.endsWith("/") && prefix !== "/" ? prefix.slice(0, -1) : prefix;
	const normalizedChild = child === "/" ? "" : child;
	const combined = `${normalizedPrefix}${normalizedChild}`;
	if (!combined) {
		return "/";
	}
	return combined.startsWith("/") ? combined : `/${combined}`;
};

const parsePathParameters = (routePath: string): OpenApiParameter[] => {
	const matches = [...routePath.matchAll(/\{([A-Za-z0-9_]+)\}/g)];
	return matches.map((match) => ({
		name: match[1] ?? "id",
		in: "path",
		required: true,
		schema: {
			type: "string",
		},
	}));
};

const toOperationId = (method: string, routePath: string) => {
	const normalized = routePath
		.replaceAll(/[{}]/g, "")
		.replaceAll("/", "_")
		.replaceAll(/[^A-Za-z0-9_]/g, "_");
	const trimmed = normalized.replaceAll(/^_+|_+$/g, "");
	return `${method.toLowerCase()}_${trimmed || "root"}`;
};

const toTag = (routePath: string) => {
	if (routePath === "/" || routePath === "/health") {
		return "system";
	}
	const segments = routePath.split("/").filter((item) => item.length > 0);
	if (segments[0] === "v1") {
		return segments[1] ?? "v1";
	}
	if (segments[0] === "internal") {
		return "internal";
	}
	return segments[0] ?? "misc";
};

const resolveServerUrl = (env: Env) => {
	if (env.APP_PUBLIC_URL) {
		return env.APP_PUBLIC_URL;
	}
	const host = env.HOST === "0.0.0.0" ? "localhost" : env.HOST;
	const protocol = env.NODE_ENV === "production" ? "https" : "http";
	return `${protocol}://${host}:${env.PORT.toString()}`;
};

const parseTopLevelObjectKeys = (objectLiteral: string) => {
	const result = new Set<string>();
	const body = objectLiteral.slice(1, -1);
	let segmentStart = 0;
	let braceDepth = 0;
	let bracketDepth = 0;
	let parenDepth = 0;
	let quote: "'" | '"' | "`" | null = null;
	let escape = false;

	const pushSegment = (segmentRaw: string) => {
		const segment = segmentRaw.trim();
		if (!segment) {
			return;
		}
		if (segment.startsWith("...")) {
			const spreadTarget = segment.slice(3).trim();
			const identifier = spreadTarget.match(/^[A-Za-z_$][A-Za-z0-9_$]*/)?.[0];
			if (identifier) {
				result.add(identifier);
			}
			return;
		}

		let colonIndex = -1;
		let localBraceDepth = 0;
		let localBracketDepth = 0;
		let localParenDepth = 0;
		let localQuote: "'" | '"' | "`" | null = null;
		let localEscape = false;
		for (let i = 0; i < segment.length; i += 1) {
			const char = segment[i];
			if (localQuote) {
				if (localEscape) {
					localEscape = false;
					continue;
				}
				if (char === "\\") {
					localEscape = true;
					continue;
				}
				if (char === localQuote) {
					localQuote = null;
				}
				continue;
			}
			if (char === "'" || char === '"' || char === "`") {
				localQuote = char;
				continue;
			}
			if (char === "{") {
				localBraceDepth += 1;
				continue;
			}
			if (char === "}") {
				localBraceDepth = Math.max(0, localBraceDepth - 1);
				continue;
			}
			if (char === "[") {
				localBracketDepth += 1;
				continue;
			}
			if (char === "]") {
				localBracketDepth = Math.max(0, localBracketDepth - 1);
				continue;
			}
			if (char === "(") {
				localParenDepth += 1;
				continue;
			}
			if (char === ")") {
				localParenDepth = Math.max(0, localParenDepth - 1);
				continue;
			}
			if (char === ":" && localBraceDepth === 0 && localBracketDepth === 0 && localParenDepth === 0) {
				colonIndex = i;
				break;
			}
		}

		const keyToken = (colonIndex >= 0 ? segment.slice(0, colonIndex) : segment).trim();
		if (!keyToken || keyToken.startsWith("[")) {
			return;
		}
		const normalized = keyToken.replaceAll(/^["'`]|["'`]$/g, "");
		const identifier = normalized.match(/^[A-Za-z_$][A-Za-z0-9_$]*/)?.[0];
		if (identifier) {
			result.add(identifier);
		}
	};

	for (let i = 0; i < body.length; i += 1) {
		const char = body[i];
		if (quote) {
			if (escape) {
				escape = false;
				continue;
			}
			if (char === "\\") {
				escape = true;
				continue;
			}
			if (char === quote) {
				quote = null;
			}
			continue;
		}
		if (char === "'" || char === '"' || char === "`") {
			quote = char;
			continue;
		}
		if (char === "{") {
			braceDepth += 1;
			continue;
		}
		if (char === "}") {
			braceDepth = Math.max(0, braceDepth - 1);
			continue;
		}
		if (char === "[") {
			bracketDepth += 1;
			continue;
		}
		if (char === "]") {
			bracketDepth = Math.max(0, bracketDepth - 1);
			continue;
		}
		if (char === "(") {
			parenDepth += 1;
			continue;
		}
		if (char === ")") {
			parenDepth = Math.max(0, parenDepth - 1);
			continue;
		}
		if (char === "," && braceDepth === 0 && bracketDepth === 0 && parenDepth === 0) {
			pushSegment(body.slice(segmentStart, i));
			segmentStart = i + 1;
		}
	}
	pushSegment(body.slice(segmentStart));
	return [...result].sort((a, b) => a.localeCompare(b));
};

const extractBalancedObject = (text: string, startIndex: number) => {
	if (text[startIndex] !== "{") {
		return null;
	}
	let braceDepth = 0;
	let quote: "'" | '"' | "`" | null = null;
	let escape = false;
	for (let i = startIndex; i < text.length; i += 1) {
		const char = text[i];
		if (quote) {
			if (escape) {
				escape = false;
				continue;
			}
			if (char === "\\") {
				escape = true;
				continue;
			}
			if (char === quote) {
				quote = null;
			}
			continue;
		}
		if (char === "'" || char === '"' || char === "`") {
			quote = char;
			continue;
		}
		if (char === "{") {
			braceDepth += 1;
			continue;
		}
		if (char === "}") {
			braceDepth -= 1;
			if (braceDepth === 0) {
				return {
					objectLiteral: text.slice(startIndex, i + 1),
					nextIndex: i + 1,
				};
			}
		}
	}
	return null;
};

const inferResponsesFromHandler = (handler: unknown) => {
	const fallback = {
		"200": {
			description: "Successful response",
			content: {
				"application/json": {
					schema: {
						type: "object",
						additionalProperties: true,
					},
				},
			},
		},
		default: {
			description: "Error response",
			content: {
				"application/json": {
					schema: {
						type: "object",
						properties: {
							code: { type: "string" },
							message: { type: "string" },
							details: {},
						},
						additionalProperties: false,
					},
				},
			},
		},
	} as Record<string, unknown>;

	if (typeof handler !== "function") {
		return fallback;
	}

	const source = handler.toString();
	const okPattern = /ok\)?\(c,\s*\{/g;
	const responseMap = new Map<string, Set<string>>();

	for (const match of source.matchAll(okPattern)) {
		const matchedText = match[0];
		const markerIndex = match.index;
		if (markerIndex === undefined) {
			continue;
		}
		const objectStart = markerIndex + matchedText.length - 1;
		const extracted = extractBalancedObject(source, objectStart);
		if (!extracted) {
			continue;
		}

		const keys = parseTopLevelObjectKeys(extracted.objectLiteral);
		let statusCode = "200";
		let i = extracted.nextIndex;
		while (i < source.length && /\s/.test(source[i] ?? "")) {
			i += 1;
		}
		if (source[i] === ",") {
			i += 1;
			while (i < source.length && /\s/.test(source[i] ?? "")) {
				i += 1;
			}
			const statusRaw = source.slice(i).match(/^\d{3}/)?.[0];
			if (statusRaw) {
				statusCode = statusRaw;
			}
		}

		if (!responseMap.has(statusCode)) {
			responseMap.set(statusCode, new Set<string>());
		}
		const statusKeys = responseMap.get(statusCode);
		if (statusKeys) {
			for (const key of keys) {
				statusKeys.add(key);
			}
		}
	}

	if (responseMap.size === 0) {
		return fallback;
	}

	const responses: Record<string, unknown> = {};
	for (const [statusCode, keys] of responseMap.entries()) {
		const sortedKeys = [...keys].sort((a, b) => a.localeCompare(b));
		const schema: OpenApiSchema =
			sortedKeys.length === 0
				? {
						type: "object",
						additionalProperties: true,
					}
				: {
						type: "object",
						properties: Object.fromEntries(sortedKeys.map((key) => [key, {}])),
						additionalProperties: false,
					};

		responses[statusCode] = {
			description: statusCode.startsWith("2") ? "Successful response" : "Error response",
			content: {
				"application/json": {
					schema,
				},
			},
		};
	}

	if (!responses.default) {
		responses.default = fallback.default;
	}

	return responses;
};

const isStringLiteralNode = (node: ts.Node | undefined): node is ts.StringLiteral | ts.NoSubstitutionTemplateLiteral =>
	Boolean(node && (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)));

const readStringLiteral = (node: ts.Node | undefined) => (isStringLiteralNode(node) ? node.text : null);

const isReqMethodCall = (node: ts.Node, method: "json" | "query" | "param") => {
	if (!ts.isCallExpression(node)) {
		return false;
	}
	if (!ts.isPropertyAccessExpression(node.expression)) {
		return false;
	}
	if (node.expression.name.text !== method) {
		return false;
	}
	const reqRef = node.expression.expression;
	return ts.isPropertyAccessExpression(reqRef) && reqRef.name.text === "req";
};

const hasUndefined = (type: ts.Type) =>
	type.isUnion()
		? type.types.some((item) => (item.flags & ts.TypeFlags.Undefined) !== 0)
		: (type.flags & ts.TypeFlags.Undefined) !== 0;

const isNullType = (type: ts.Type) => (type.flags & ts.TypeFlags.Null) !== 0;

const isLiteralUnion = (types: ts.Type[]) => types.every((item) => item.isLiteral());

const inferBooleanLiteral = (type: ts.Type): boolean | null => {
	if ((type.flags & ts.TypeFlags.BooleanLiteral) === 0) {
		return null;
	}
	return (type as { intrinsicName?: string }).intrinsicName === "true";
};

const typeToSchema = (
	type: ts.Type,
	checker: ts.TypeChecker,
	location: ts.Node,
	seen: Set<number>,
	depth = 0,
): OpenApiSchema => {
	if (depth > 10) {
		return {};
	}

	const typeId = (type as { id?: number }).id;
	if (typeId !== undefined) {
		if (seen.has(typeId)) {
			return {};
		}
		seen.add(typeId);
	}

	if (
		(type.flags & ts.TypeFlags.Any) !== 0 ||
		(type.flags & ts.TypeFlags.Unknown) !== 0 ||
		(type.flags & ts.TypeFlags.Never) !== 0
	) {
		return {};
	}

	if ((type.flags & ts.TypeFlags.String) !== 0 || (type.flags & ts.TypeFlags.StringLike) !== 0) {
		return { type: "string" };
	}
	if ((type.flags & ts.TypeFlags.Number) !== 0 || (type.flags & ts.TypeFlags.NumberLike) !== 0) {
		return { type: "number" };
	}
	if ((type.flags & ts.TypeFlags.Boolean) !== 0 || (type.flags & ts.TypeFlags.BooleanLike) !== 0) {
		return { type: "boolean" };
	}
	if ((type.flags & ts.TypeFlags.BigIntLike) !== 0) {
		return { type: "string" };
	}

	if ((type.flags & ts.TypeFlags.StringLiteral) !== 0) {
		return { type: "string", enum: [(type as ts.StringLiteralType).value] };
	}
	if ((type.flags & ts.TypeFlags.NumberLiteral) !== 0) {
		return { type: "number", enum: [(type as ts.NumberLiteralType).value] };
	}
	if ((type.flags & ts.TypeFlags.BooleanLiteral) !== 0) {
		const value = inferBooleanLiteral(type);
		if (value !== null) {
			return { type: "boolean", enum: [value] };
		}
	}

	if (type.isUnion()) {
		const withoutUndefined = type.types.filter((item) => (item.flags & ts.TypeFlags.Undefined) === 0);
		const nullable = withoutUndefined.some(isNullType);
		const withoutNull = withoutUndefined.filter((item) => !isNullType(item));

		if (withoutNull.length === 0) {
			return { nullable: true };
		}

		if (isLiteralUnion(withoutNull)) {
			const strings = withoutNull.filter((item) => (item.flags & ts.TypeFlags.StringLiteral) !== 0);
			const numbers = withoutNull.filter((item) => (item.flags & ts.TypeFlags.NumberLiteral) !== 0);
			const booleans = withoutNull.filter((item) => (item.flags & ts.TypeFlags.BooleanLiteral) !== 0);

			if (strings.length === withoutNull.length) {
				return {
					type: "string",
					enum: strings.map((item) => (item as ts.StringLiteralType).value),
					...(nullable ? { nullable: true } : {}),
				};
			}
			if (numbers.length === withoutNull.length) {
				return {
					type: "number",
					enum: numbers.map((item) => (item as ts.NumberLiteralType).value),
					...(nullable ? { nullable: true } : {}),
				};
			}
			if (booleans.length === withoutNull.length) {
				const boolValues = booleans.map((item) => inferBooleanLiteral(item)).filter((item): item is boolean => item !== null);
				return {
					type: "boolean",
					enum: boolValues,
					...(nullable ? { nullable: true } : {}),
				};
			}
		}

		const oneOf = withoutNull.map((item) => typeToSchema(item, checker, location, new Set(seen), depth + 1));
		const firstSchema = oneOf[0] ?? {};
		if (oneOf.length === 1) {
			return nullable ? { ...firstSchema, nullable: true } : firstSchema;
		}
		return {
			oneOf,
			...(nullable ? { nullable: true } : {}),
		};
	}

	if (type.isIntersection()) {
		return {
			allOf: type.types.map((item) => typeToSchema(item, checker, location, new Set(seen), depth + 1)),
		};
	}

	const typeRef = type as ts.TypeReference;
	const symbolName = type.getSymbol()?.getName();

	if (symbolName === "Date") {
		return { type: "string", format: "date-time" };
	}
	if (symbolName === "Uint8Array" || symbolName === "Buffer") {
		return { type: "string", format: "byte" };
	}
	if (symbolName === "Promise") {
		const typeArgs = checker.getTypeArguments(typeRef);
		const firstTypeArg = typeArgs[0];
		if (firstTypeArg) {
			return typeToSchema(firstTypeArg, checker, location, new Set(seen), depth + 1);
		}
	}

	if (checker.isArrayType(type)) {
		const typeArgs = checker.getTypeArguments(typeRef);
		const itemType = typeArgs[0];
		return {
			type: "array",
			items: itemType ? typeToSchema(itemType, checker, location, new Set(seen), depth + 1) : {},
		};
	}

	if (checker.isTupleType(type)) {
		const typeArgs = checker.getTypeArguments(typeRef);
		return {
			type: "array",
			items: typeArgs.map((item) => typeToSchema(item, checker, location, new Set(seen), depth + 1)),
			minItems: typeArgs.length,
			maxItems: typeArgs.length,
		};
	}

	const properties = checker
		.getPropertiesOfType(type)
		.filter((prop) => (prop.flags & ts.SymbolFlags.Method) === 0 && !prop.getName().startsWith("__@"));

	if (properties.length > 0) {
		const propertySchemas: Record<string, OpenApiSchema> = {};
		const required: string[] = [];

		for (const prop of properties) {
			const declaration = prop.valueDeclaration ?? prop.declarations?.[0] ?? location;
			const propType = checker.getTypeOfSymbolAtLocation(prop, declaration);
			const propSchema = typeToSchema(propType, checker, declaration, new Set(seen), depth + 1);
			propertySchemas[prop.getName()] = propSchema;

			const optional = (prop.flags & ts.SymbolFlags.Optional) !== 0 || hasUndefined(propType);
			if (!optional) {
				required.push(prop.getName());
			}
		}

		return {
			type: "object",
			properties: propertySchemas,
			...(required.length > 0 ? { required } : {}),
			additionalProperties: false,
		};
	}

	const indexType = checker.getIndexTypeOfType(type, ts.IndexKind.String);
	if (indexType) {
		return {
			type: "object",
			additionalProperties: typeToSchema(indexType, checker, location, new Set(seen), depth + 1),
		};
	}

	const text = checker.typeToString(type, location, ts.TypeFormatFlags.NoTruncation);
	if (text === "string") {
		return { type: "string" };
	}
	if (text === "number") {
		return { type: "number" };
	}
	if (text === "boolean") {
		return { type: "boolean" };
	}

	return {};
};

const resolveStatusCode = (node: ts.Expression | undefined, fallback: string) => {
	if (!node) {
		return fallback;
	}
	if (ts.isNumericLiteral(node)) {
		return node.text;
	}
	if (ts.isPrefixUnaryExpression(node) && node.operator === ts.SyntaxKind.MinusToken && ts.isNumericLiteral(node.operand)) {
		return `-${node.operand.text}`;
	}
	return fallback;
};

const buildDefaultErrorResponse = () => ({
	description: "Error response",
	content: {
		"application/json": {
			schema: {
				type: "object",
				properties: {
					code: { type: "string" },
					message: { type: "string" },
					details: {},
				},
				additionalProperties: false,
			},
		},
	},
});

const inferQuerySchemaFromContext = (node: ts.CallExpression): OpenApiSchema => {
	let current: ts.Node | undefined = node;
	for (let depth = 0; depth < 4 && current; depth += 1) {
		const parentNode: ts.Node | undefined = current.parent;
		if (!parentNode) {
			break;
		}

		if (ts.isCallExpression(parentNode) && ts.isIdentifier(parentNode.expression)) {
			if (parentNode.expression.text === "Number") {
				return { type: "number" };
			}
			if (parentNode.expression.text === "Boolean") {
				return { type: "boolean" };
			}
		}

		if (ts.isNewExpression(parentNode) && ts.isIdentifier(parentNode.expression) && parentNode.expression.text === "Date") {
			return { type: "string", format: "date-time" };
		}

		if (ts.isBinaryExpression(parentNode)) {
			const operator = parentNode.operatorToken.kind;
			const counterpart = parentNode.left === current ? parentNode.right : parentNode.left;
			if (
				(operator === ts.SyntaxKind.EqualsEqualsEqualsToken || operator === ts.SyntaxKind.ExclamationEqualsEqualsToken) &&
				ts.isStringLiteral(counterpart) &&
				(counterpart.text === "true" || counterpart.text === "false")
			) {
				return { type: "boolean" };
			}

			if (operator === ts.SyntaxKind.QuestionQuestionToken) {
				if (ts.isNumericLiteral(counterpart)) {
					return { type: "number" };
				}
				if (counterpart.kind === ts.SyntaxKind.TrueKeyword || counterpart.kind === ts.SyntaxKind.FalseKeyword) {
					return { type: "boolean" };
				}
			}
		}

		current = parentNode;
	}

	return { type: "string" };
};

const mergeParameters = (parameters: OpenApiParameter[]) => {
	const map = new Map<string, OpenApiParameter>();
	for (const parameter of parameters) {
		const key = `${parameter.in}:${parameter.name}`;
		const existing = map.get(key);
		if (!existing) {
			map.set(key, parameter);
			continue;
		}

		const existingType = (existing.schema.type as string | undefined) ?? "";
		const incomingType = (parameter.schema.type as string | undefined) ?? "";
		if (existingType === "string" && incomingType && incomingType !== "string") {
			map.set(key, parameter);
		}
	}
	return [...map.values()];
};

const extractObjectTypeParameters = (
	type: ts.Type,
	checker: ts.TypeChecker,
	location: ts.Node,
	where: "query" | "path",
): OpenApiParameter[] => {
	const properties = checker
		.getPropertiesOfType(type)
		.filter((prop) => (prop.flags & ts.SymbolFlags.Method) === 0 && !prop.getName().startsWith("__@"));

	return properties.map((prop) => {
		const declaration = prop.valueDeclaration ?? prop.declarations?.[0] ?? location;
		const propType = checker.getTypeOfSymbolAtLocation(prop, declaration);
		const required = (prop.flags & ts.SymbolFlags.Optional) === 0 && !hasUndefined(propType);
		return {
			name: prop.getName(),
			in: where,
			required,
			schema: typeToSchema(propType, checker, declaration, new Set()),
		};
	});
};

const analyzeHandlerWithTypes = (
	handler: ts.ArrowFunction | ts.FunctionExpression,
	checker: ts.TypeChecker,
): Omit<TypedOperation, "responses"> & { responses: Record<string, unknown> } => {
	const queryParameters = new Map<string, OpenApiParameter>();
	const responseSchemas = new Map<string, OpenApiSchema[]>();
	let requestBody: Record<string, unknown> | undefined;

	const addResponse = (status: string, schema: OpenApiSchema | null) => {
		if (!responseSchemas.has(status)) {
			responseSchemas.set(status, []);
		}
		if (schema) {
			responseSchemas.get(status)?.push(schema);
		}
	};

	const handleReturnExpression = (expression: ts.Expression | undefined) => {
		if (!expression) {
			return;
		}

		if (ts.isCallExpression(expression)) {
			if (ts.isIdentifier(expression.expression) && expression.expression.text === "ok") {
				const dataArg = expression.arguments[1];
				const status = resolveStatusCode(expression.arguments[2], "200");
				if (dataArg) {
					const dataType = checker.getTypeAtLocation(dataArg);
					addResponse(status, typeToSchema(dataType, checker, dataArg, new Set()));
				} else {
					addResponse(status, null);
				}
				return;
			}

			if (ts.isPropertyAccessExpression(expression.expression)) {
				const method = expression.expression.name.text;
				if (method === "json") {
					const dataArg = expression.arguments[0];
					const status = resolveStatusCode(expression.arguments[1], "200");
					if (dataArg) {
						const dataType = checker.getTypeAtLocation(dataArg);
						addResponse(status, typeToSchema(dataType, checker, dataArg, new Set()));
					} else {
						addResponse(status, null);
					}
					return;
				}

				if (method === "redirect") {
					const status = resolveStatusCode(expression.arguments[1], "302");
					addResponse(status, null);
					return;
				}
			}
		}

		if (ts.isNewExpression(expression) && ts.isIdentifier(expression.expression) && expression.expression.text === "Response") {
			const initArg = expression.arguments?.[1];
			let status = "200";
			if (initArg && ts.isObjectLiteralExpression(initArg)) {
				const statusProperty = initArg.properties.find(
					(item) => ts.isPropertyAssignment(item) && ts.isIdentifier(item.name) && item.name.text === "status",
				);
				if (statusProperty && ts.isPropertyAssignment(statusProperty)) {
					status = resolveStatusCode(statusProperty.initializer as ts.Expression, "200");
				}
			}
			addResponse(status, {
				type: "string",
				format: "binary",
			});
		}
	};

	const visit = (node: ts.Node) => {
		if (ts.isVariableDeclaration(node) && node.initializer) {
			if (
				ts.isCallExpression(node.initializer) &&
				ts.isPropertyAccessExpression(node.initializer.expression) &&
				node.initializer.expression.name.text === "parse"
			) {
				const parseArg = node.initializer.arguments[0];
				if (parseArg) {
					if (
						(ts.isAwaitExpression(parseArg) && isReqMethodCall(parseArg.expression, "json")) ||
						isReqMethodCall(parseArg, "json")
					) {
						const bodyType = checker.getTypeAtLocation(node.name);
						requestBody = {
							required: true,
							content: {
								"application/json": {
									schema: typeToSchema(bodyType, checker, node.name, new Set()),
								},
							},
						};
					}

					if (isReqMethodCall(parseArg, "query")) {
						const queryType = checker.getTypeAtLocation(node.name);
						for (const parameter of extractObjectTypeParameters(queryType, checker, node.name, "query")) {
							queryParameters.set(parameter.name, parameter);
						}
					}
				}
			}
		}

		if (ts.isCallExpression(node) && isReqMethodCall(node, "query") && node.arguments.length === 1) {
			const name = readStringLiteral(node.arguments[0]);
			if (name) {
				queryParameters.set(name, {
					name,
					in: "query",
					required: false,
					schema: inferQuerySchemaFromContext(node),
				});
			}
		}

		if (ts.isReturnStatement(node)) {
			handleReturnExpression(node.expression);
		}

		ts.forEachChild(node, visit);
	};

	if (ts.isBlock(handler.body)) {
		ts.forEachChild(handler.body, visit);
	} else {
		handleReturnExpression(handler.body);
	}

	const responses: Record<string, unknown> = {};
	for (const [status, schemas] of responseSchemas.entries()) {
		const deduped = [...new Map(schemas.map((schema) => [JSON.stringify(schema), schema])).values()];
		if (deduped.length === 0) {
			responses[status] = {
				description: status.startsWith("3") ? "Redirect response" : "Response",
			};
			continue;
		}

		responses[status] = {
			description: status.startsWith("2")
				? "Successful response"
				: status.startsWith("3")
					? "Redirect response"
					: "Error response",
			content: {
				"application/json": {
					schema: deduped.length === 1 ? deduped[0] : { oneOf: deduped },
				},
			},
		};
	}

	if (!responses.default) {
		responses.default = buildDefaultErrorResponse();
	}

	return {
		parameters: [...queryParameters.values()],
		...(requestBody ? { requestBody } : {}),
		responses,
	};
};

let typedOperationCache: Map<string, TypedOperation> | null = null;

const buildTypedOperationMap = () => {
	const result = new Map<string, TypedOperation>();

	const moduleDir = path.dirname(fileURLToPath(import.meta.url));
	const projectRoot = path.resolve(moduleDir, "..");
	const tsconfigPath = path.join(projectRoot, "tsconfig.build.json");
	if (!existsSync(tsconfigPath)) {
		return result;
	}

	const configFile = ts.readConfigFile(tsconfigPath, ts.sys.readFile);
	if (configFile.error) {
		return result;
	}

	const parsed = ts.parseJsonConfigFileContent(configFile.config, ts.sys, projectRoot);
	const program = ts.createProgram({
		rootNames: parsed.fileNames,
		options: parsed.options,
	});
	const checker = program.getTypeChecker();

	const appSource = program.getSourceFiles().find((source) => /[/\\]src[/\\]app\.ts$/.test(source.fileName));

	const routePrefixes = new Map<string, string>();
	if (appSource) {
		const visitApp = (node: ts.Node) => {
			if (ts.isCallExpression(node) && ts.isPropertyAccessExpression(node.expression)) {
				const target = node.expression.expression;
				const method = node.expression.name.text;
				if (method === "route" && ts.isIdentifier(target) && target.text === "app") {
					const prefix = readStringLiteral(node.arguments[0]);
					const routeRef = node.arguments[1];
					if (prefix && routeRef && ts.isIdentifier(routeRef)) {
						routePrefixes.set(routeRef.text, prefix);
					}
				}
			}
			ts.forEachChild(node, visitApp);
		};
		ts.forEachChild(appSource, visitApp);
	}

	for (const sourceFile of program.getSourceFiles()) {
		if (sourceFile.isDeclarationFile) {
			continue;
		}
		if (!/[/\\]src[/\\]routes[/\\].+\.ts$/.test(sourceFile.fileName)) {
			continue;
		}

		const routeVars = new Set<string>();
		const collectRouteVars = (node: ts.Node) => {
			if (
				ts.isVariableDeclaration(node) &&
				ts.isIdentifier(node.name) &&
				node.initializer &&
				ts.isNewExpression(node.initializer) &&
				ts.isIdentifier(node.initializer.expression) &&
				node.initializer.expression.text === "Hono"
			) {
				routeVars.add(node.name.text);
			}
			ts.forEachChild(node, collectRouteVars);
		};

		const collectOperations = (node: ts.Node) => {
			if (ts.isCallExpression(node) && ts.isPropertyAccessExpression(node.expression)) {
				const target = node.expression.expression;
				const method = node.expression.name.text;
				if (ts.isIdentifier(target) && routeVars.has(target.text) && METHOD_CALLS.has(method)) {
					const routePath = readStringLiteral(node.arguments[0]);
					const handlerNode = node.arguments[node.arguments.length - 1];
					if (routePath && handlerNode && (ts.isArrowFunction(handlerNode) || ts.isFunctionExpression(handlerNode))) {
						const prefix = routePrefixes.get(target.text) ?? "";
						const fullPath = normalizePath(joinRoutePath(prefix, routePath));
						const analyzed = analyzeHandlerWithTypes(handlerNode, checker);
						const pathParameters = parsePathParameters(fullPath);
						const queryParameters = analyzed.parameters ?? [];
						const parameters = mergeParameters([...pathParameters, ...queryParameters]);

						result.set(`${method.toUpperCase()} ${fullPath}`, {
							...(parameters.length > 0 ? { parameters } : {}),
							...(analyzed.requestBody ? { requestBody: analyzed.requestBody } : {}),
							responses: analyzed.responses,
						});
					}
				}
			}
			ts.forEachChild(node, collectOperations);
		};

		ts.forEachChild(sourceFile, collectRouteVars);
		ts.forEachChild(sourceFile, collectOperations);
	}

	return result;
};

const getTypedOperationMap = () => {
	if (typedOperationCache) {
		return typedOperationCache;
	}
	try {
		typedOperationCache = buildTypedOperationMap();
	} catch (error) {
		console.warn("[openapi] typed extraction failed, fallback to runtime inference", error);
		typedOperationCache = new Map<string, TypedOperation>();
	}
	return typedOperationCache;
};

export const buildOpenApiDocument = (app: Hono<AppEnv>, env: Env): OpenApiDocument => {
	const paths: Record<string, OpenApiPathItem> = {};
	const tags = new Set<string>();
	const routeMap = new Map<string, RouteEntry>();
	const typedOperationMap = getTypedOperationMap();

	for (const route of app.routes as RouteEntry[]) {
		const method = route.method.toUpperCase();
		if (!DOCUMENTED_METHODS.has(method)) {
			continue;
		}
		if (route.path.includes("*")) {
			continue;
		}

		const routePath = normalizePath(route.path);
		const dedupeKey = `${method} ${routePath}`;
		routeMap.set(dedupeKey, route);
	}

	for (const [dedupeKey, route] of routeMap.entries()) {
		const [method, routePath] = dedupeKey.split(" ");
		if (!method || !routePath) {
			continue;
		}

		const tag = toTag(routePath);
		tags.add(tag);

		const typedOperation = typedOperationMap.get(dedupeKey);
		const fallbackParameters = parsePathParameters(routePath);
		const mergedParameters = mergeParameters([
			...fallbackParameters,
			...((typedOperation?.parameters as OpenApiParameter[] | undefined) ?? []),
		]);

		const operation: Record<string, unknown> = {
			operationId: toOperationId(method, routePath),
			tags: [tag],
			summary: `${method} ${routePath}`,
			...(mergedParameters.length > 0
				? {
						parameters: mergedParameters,
					}
				: {}),
			...(typedOperation?.requestBody ? { requestBody: typedOperation.requestBody } : {}),
			responses: typedOperation?.responses ?? inferResponsesFromHandler(route.handler),
		};

		if (!paths[routePath]) {
			paths[routePath] = {};
		}
		paths[routePath][method.toLowerCase()] = operation;
	}

	return {
		openapi: "3.1.0",
		info: {
			title: "maimaid backend API",
			description:
				"Auto-generated API specification from registered Hono routes and TypeScript route-source analysis. Schemas are inferred from actual handler input/output types.",
			version: "0.1.0",
		},
		servers: [
			{
				url: resolveServerUrl(env),
				description: "Current backend server",
			},
		],
		tags: [...tags].sort((a, b) => a.localeCompare(b)).map((name) => ({ name })),
		paths,
	};
};
