import { Hono } from "hono";
import { describe, expect, it } from "vitest";
import { ok } from "../src/http/response.js";
import type { AppEnv } from "../src/types/hono.js";

describe("ok response helper", () => {
	it("sets content-length based on UTF-8 response bytes", async () => {
		const app = new Hono<AppEnv>();
		app.get("/bundle/:version", (c) => ok(c, { version: c.req.param("version"), message: "你好" }));

		const response = await app.request("http://localhost/bundle/latest");
		const body = await response.text();
		const expectedContentLength = new TextEncoder().encode(body).byteLength.toString();

		expect(response.headers.get("content-type")).toBe("application/json; charset=UTF-8");
		expect(response.headers.get("content-length")).toBe(expectedContentLength);
	});
});
