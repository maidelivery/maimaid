import { Hono, type Context } from "hono";
import { z } from "zod";
import { authRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import { standardValidator, validationHook } from "../../http/validation.js";
import { LetterGameService } from "../../services/letter-game.service.js";
import type { AppEnv } from "../../types/hono.js";

const roomParamSchema = z.object({ roomId: z.string().min(1).max(64) });
const memberParamSchema = z.object({ roomId: z.uuid(), memberId: z.uuid() });
const createRoomSchema = z.object({
	visibility: z.enum(["public", "private"]),
	hostMode: z.enum(["fixed", "rotate"]).default("fixed"),
	turnDurationSeconds: z.number().int().min(15).max(120).default(30),
	stalledRoundLimit: z.number().int().min(1).max(10).default(3),
	songCount: z.number().int().min(1).max(5000).nullable().optional(),
	publicHintCost: z.number().int().min(1).max(100).default(5),
	privateHintCost: z.number().int().min(1).max(100).default(10),
	selectionMode: z.enum(["filtered_random", "collection", "favorites"]).default("filtered_random"),
	selectionConfig: z.record(z.string(), z.unknown()).default({}),
});
const joinSchema = z.object({ code: z.string().length(6) });

export const letterGameV1Route = new Hono<AppEnv>();
letterGameV1Route.use("*", authRequired);

const requireUser = (c: Context<AppEnv>) => {
	const auth = c.get("auth");
	if (!auth) throw new Error("Authentication required.");
	return auth.userId;
};

letterGameV1Route.get("/rooms", async (c) => {
	const service = c.var.resolve(LetterGameService);
	return ok(c, { rooms: await service.listPublicRooms() });
});

letterGameV1Route.post("/rooms", standardValidator("json", createRoomSchema, validationHook), async (c) => {
	const service = c.var.resolve(LetterGameService);
	const body = c.req.valid("json");
	return ok(c, { room: await service.createRoom(requireUser(c), body) }, 201);
});

letterGameV1Route.post("/rooms:join", standardValidator("json", joinSchema, validationHook), async (c) => {
	const service = c.var.resolve(LetterGameService);
	const body = c.req.valid("json");
	return ok(c, { room: await service.joinRoom(requireUser(c), body.code) });
});

letterGameV1Route.get("/rooms/:roomId", standardValidator("param", roomParamSchema, validationHook), async (c) => {
	const service = c.var.resolve(LetterGameService);
	return ok(c, { room: await service.getRoom(requireUser(c), c.req.valid("param").roomId) });
});

letterGameV1Route.post("/rooms/:roomId/start", standardValidator("param", roomParamSchema, validationHook), async (c) => {
	const service = c.var.resolve(LetterGameService);
	return ok(c, { match: await service.startMatch(requireUser(c), c.req.valid("param").roomId) }, 201);
});

letterGameV1Route.post("/rooms/:roomId/reopen", standardValidator("param", roomParamSchema, validationHook), async (c) => {
	const service = c.var.resolve(LetterGameService);
	return ok(c, { room: await service.prepareReopen(requireUser(c), c.req.valid("param").roomId) });
});

letterGameV1Route.patch(
	"/rooms/:roomId",
	standardValidator("param", roomParamSchema, validationHook),
	standardValidator("json", createRoomSchema.partial(), validationHook),
	async (c) => {
		const service = c.var.resolve(LetterGameService);
		return ok(c, {
			room: await service.updateSettings(
				requireUser(c),
				c.req.valid("param").roomId,
				c.req.valid("json") as unknown as Parameters<LetterGameService["updateSettings"]>[2],
			),
		});
	},
);

letterGameV1Route.post("/rooms/:roomId/leave", standardValidator("param", roomParamSchema, validationHook), async (c) => {
	const service = c.var.resolve(LetterGameService);
	return ok(c, await service.leaveRoom(requireUser(c), c.req.valid("param").roomId));
});

letterGameV1Route.get("/rooms/:roomId/history", standardValidator("param", roomParamSchema, validationHook), async (c) => {
	const service = c.var.resolve(LetterGameService);
	return ok(c, { matches: await service.history(requireUser(c), c.req.valid("param").roomId) });
});

letterGameV1Route.post(
	"/rooms/:roomId/members/:memberId/approve",
	standardValidator("param", memberParamSchema, validationHook),
	async (c) => {
		const service = c.var.resolve(LetterGameService);
		const params = c.req.valid("param");
		return ok(c, await service.approveMember(requireUser(c), params.roomId, params.memberId));
	},
);

letterGameV1Route.post(
	"/rooms/:roomId/members/:memberId/kick",
	standardValidator("param", memberParamSchema, validationHook),
	async (c) => {
		const service = c.var.resolve(LetterGameService);
		const params = c.req.valid("param");
		return ok(c, { room: await service.kickMember(requireUser(c), params.roomId, params.memberId) });
	},
);

letterGameV1Route.get("/rooms/:roomId/ws", standardValidator("param", roomParamSchema, validationHook), async (c) =>
	ok(c, { code: "websocket_upgrade_required", message: "Use a WebSocket connection for this endpoint." }, 426),
);
