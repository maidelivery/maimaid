import { Hono, type Context } from "hono";
import { z } from "zod";
import { authRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import { standardValidator, validationHook } from "../../http/validation.js";
import { LetterGameService } from "../../services/letter-game.service.js";
import { LetterGameConnectionHub } from "../../services/letter-game.connection.js";
import type { AppEnv } from "../../types/hono.js";

const roomParamSchema = z.object({ roomId: z.string().min(1).max(64) });
const matchParamSchema = z.object({ matchId: z.uuid() });
const memberParamSchema = z.object({ roomId: z.uuid(), memberId: z.uuid() });
const selectionConfigSchema = z
	.object({
		excludeDeleted: z.boolean().optional(),
		englishOnly: z.boolean().optional(),
		minVersion: z.string().trim().min(1).max(100).nullable().optional(),
		maxVersion: z.string().trim().min(1).max(100).nullable().optional(),
		categories: z.array(z.string().trim().min(1).max(100)).max(100).optional(),
		chartTypes: z.array(z.enum(["standard", "dx"])).max(2).optional(),
		collectionIds: z.array(z.uuid()).max(100).optional(),
	})
	.strict();
const createRoomSchema = z.object({
	visibility: z.enum(["public", "private"]),
	hostMode: z.enum(["fixed", "rotate"]).default("fixed"),
	turnDurationSeconds: z.number().int().min(15).max(120).default(30),
	stalledRoundLimit: z.number().int().min(1).max(10).default(3),
	songCount: z.number().int().min(1).max(5000).nullable().optional(),
	publicHintCost: z.number().int().min(1).max(100).default(5),
	privateHintCost: z.number().int().min(1).max(100).default(10),
	selectionMode: z.enum(["filtered_random", "collection"]).default("filtered_random"),
	selectionConfig: selectionConfigSchema.default({}),
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
	const room = await service.joinRoom(requireUser(c), body.code);
	await c.var.resolve(LetterGameConnectionHub).broadcastRoom(room.id);
	return ok(c, { room });
});

letterGameV1Route.get("/rooms/:roomId", standardValidator("param", roomParamSchema, validationHook), async (c) => {
	const service = c.var.resolve(LetterGameService);
	return ok(c, { room: await service.getRoom(requireUser(c), c.req.valid("param").roomId) });
});

letterGameV1Route.post("/rooms/:roomId/start", standardValidator("param", roomParamSchema, validationHook), async (c) => {
	const service = c.var.resolve(LetterGameService);
	const roomId = c.req.valid("param").roomId;
	const match = await service.startMatch(requireUser(c), roomId);
	await c.var.resolve(LetterGameConnectionHub).broadcastRoom(roomId);
	return ok(c, { match }, 201);
});

letterGameV1Route.post("/rooms/:roomId/reopen", standardValidator("param", roomParamSchema, validationHook), async (c) => {
	const service = c.var.resolve(LetterGameService);
	const roomId = c.req.valid("param").roomId;
	const room = await service.prepareReopen(requireUser(c), roomId);
	await c.var.resolve(LetterGameConnectionHub).broadcastRoom(roomId);
	return ok(c, { room });
});

letterGameV1Route.patch(
	"/rooms/:roomId",
	standardValidator("param", roomParamSchema, validationHook),
	standardValidator("json", createRoomSchema.partial(), validationHook),
	async (c) => {
		const service = c.var.resolve(LetterGameService);
		const roomId = c.req.valid("param").roomId;
		const room = await service.updateSettings(
				requireUser(c),
				roomId,
				c.req.valid("json") as unknown as Parameters<LetterGameService["updateSettings"]>[2],
		);
		await c.var.resolve(LetterGameConnectionHub).broadcastRoom(roomId);
		return ok(c, { room });
	},
);

letterGameV1Route.post("/rooms/:roomId/leave", standardValidator("param", roomParamSchema, validationHook), async (c) => {
	const service = c.var.resolve(LetterGameService);
	const roomId = c.req.valid("param").roomId;
	const result = await service.leaveRoom(requireUser(c), roomId);
	const hub = c.var.resolve(LetterGameConnectionHub);
	if (result.dissolved) hub.closeRoom(roomId);
	else await hub.broadcastRoom(roomId);
	return ok(c, result);
});

letterGameV1Route.get("/rooms/:roomId/history", standardValidator("param", roomParamSchema, validationHook), async (c) => {
	const service = c.var.resolve(LetterGameService);
	return ok(c, { matches: await service.history(requireUser(c), c.req.valid("param").roomId) });
});

letterGameV1Route.get("/matches/:matchId", standardValidator("param", matchParamSchema, validationHook), async (c) => {
	const service = c.var.resolve(LetterGameService);
	return ok(c, { match: await service.getMatchSnapshot(requireUser(c), c.req.valid("param").matchId) });
});

letterGameV1Route.post(
	"/rooms/:roomId/members/:memberId/approve",
	standardValidator("param", memberParamSchema, validationHook),
	async (c) => {
		const service = c.var.resolve(LetterGameService);
		const params = c.req.valid("param");
		const result = await service.approveMember(requireUser(c), params.roomId, params.memberId);
		await c.var.resolve(LetterGameConnectionHub).broadcastRoom(params.roomId);
		return ok(c, result);
	},
);

letterGameV1Route.post(
	"/rooms/:roomId/members/:memberId/kick",
	standardValidator("param", memberParamSchema, validationHook),
	async (c) => {
		const service = c.var.resolve(LetterGameService);
		const params = c.req.valid("param");
		const room = await service.kickMember(requireUser(c), params.roomId, params.memberId);
		await c.var.resolve(LetterGameConnectionHub).broadcastRoom(params.roomId);
		return ok(c, { room });
	},
);

letterGameV1Route.get("/rooms/:roomId/ws", standardValidator("param", roomParamSchema, validationHook), async (c) =>
	ok(c, { code: "websocket_upgrade_required", message: "Use a WebSocket connection for this endpoint." }, 426),
);
