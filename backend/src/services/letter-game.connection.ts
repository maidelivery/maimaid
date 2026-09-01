/* eslint-disable @typescript-eslint/no-explicit-any */
import type { IncomingMessage, Server } from "node:http";
import { randomUUID } from "node:crypto";
import { WebSocketServer, WebSocket } from "ws";
import { container, injectable } from "tsyringe";
import { z } from "zod";
import { JwtService } from "./jwt.service.js";
import { LetterGameService } from "./letter-game.service.js";
import type { LetterGameActionInput } from "./letter-game.service.js";

type Connection = {
	ws: WebSocket;
	userId: string;
	roomId: string;
	roomCode: string;
};

const send = (ws: WebSocket, payload: unknown) => {
	if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(payload));
};

const unauthorized = (socket: { write: (value: string) => void; destroy: () => void }) => {
	socket.write("HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n");
	socket.destroy();
};

const actionPayloadSchema = z.discriminatedUnion("kind", [
	z.object({ kind: z.literal("open_character"), character: z.string().min(1).max(256) }),
	z.object({ kind: z.literal("guess_song"), slotId: z.string().min(1).max(128), guess: z.string().trim().min(1).max(256) }),
	z.object({
		kind: z.literal("buy_hint"),
		slotId: z.string().min(1).max(128),
		hintType: z.enum(["version", "constant", "white_chart"]),
		visibility: z.enum(["public", "private"]),
		difficulty: z.string().trim().min(1).max(32).optional(),
	}),
]);

const actionMessageSchema = z.object({
	type: z.literal("action"),
	matchId: z.uuid(),
	actionId: z.string().trim().min(1).max(128),
	expectedRevision: z.number().int().nonnegative().optional(),
	payload: actionPayloadSchema,
});

const directActionMessageSchema = z.object({
	type: z.enum(["open_character", "guess_song", "buy_hint"]),
	matchId: z.uuid(),
	actionId: z.string().trim().min(1).max(128).optional(),
	expectedRevision: z.number().int().nonnegative().optional(),
	payload: z.record(z.string(), z.unknown()).optional(),
});

@injectable()
export class LetterGameConnectionHub {
	private readonly webSocketServer = new WebSocketServer({ noServer: true });
	private readonly connections = new Map<string, Set<Connection>>();
	private readonly service = container.resolve(LetterGameService);
	private readonly jwt = container.resolve(JwtService);

	attach(server: Server) {
		const heartbeat = setInterval(() => {
			for (const connections of this.connections.values()) {
				for (const connection of connections) {
					if (connection.ws.readyState === WebSocket.OPEN) connection.ws.ping();
				}
			}
		}, 20_000);
		heartbeat.unref?.();
		server.on("upgrade", (request, socket, head) => {
			const url = new URL(request.url ?? "/", "http://localhost");
			const match = url.pathname.match(/^\/v1\/letter-game\/rooms\/([^/]+)\/ws$/u);
			if (!match) return;
			if (!request.headers.authorization?.startsWith("Bearer ")) {
				unauthorized(socket);
				return;
			}
			this.webSocketServer.handleUpgrade(request, socket, head, (ws) => {
				void this.handleConnection(ws, request, match[1] ?? "");
			});
		});
	}

	async broadcastRoom(roomId: string) {
		const connections = [...this.connections.values()]
			.flatMap((items) => [...items])
			.filter((connection) => connection.roomId === roomId);
		await Promise.all(
			connections.map(async (connection) => {
				const room = await this.service.getRoom(connection.userId, roomId).catch(() => null);
				if (room) {
					send(connection.ws, { type: "room_snapshot", room });
					const latestMatchId = room.latestMatch?.id;
					if (latestMatchId) {
						const match = await this.service.getMatchSnapshot(connection.userId, latestMatchId).catch(() => null);
						if (match) send(connection.ws, { type: "match_snapshot", match });
					}
				} else {
					connection.ws.close(1008, "room_access_denied");
				}
			}),
		);
	}

	closeRoom(roomId: string) {
		const connections = this.connections.get(roomId);
		if (!connections) return;
		for (const connection of connections) {
			send(connection.ws, { type: "room_dissolved", roomId });
			connection.ws.close(1000, "room_dissolved");
		}
		this.connections.delete(roomId);
	}

	private async handleConnection(ws: WebSocket, request: IncomingMessage, roomCode: string) {
		const authorization = request.headers.authorization;
		if (!authorization?.startsWith("Bearer ")) return ws.close(1008, "unauthorized");
		let userId: string;
		try {
			const payload = await this.jwt.verifyAccessToken(authorization.replace(/^Bearer\s+/iu, ""));
			userId = payload.sub;
		} catch {
			ws.close(1008, "unauthorized");
			return;
		}

		const room = await this.service.getRoom(userId, roomCode).catch(() => null);
		const member = room?.members.find((item) => item.userId === userId && item.status === "accepted");
		if (!room || !member) {
			ws.close(1008, "room_access_denied");
			return;
		}

		const connection: Connection = { ws, userId, roomId: room.id, roomCode: room.code };
		const roomConnections = this.connections.get(room.id) ?? new Set<Connection>();
		roomConnections.add(connection);
		this.connections.set(room.id, roomConnections);
		ws.on("close", () => {
			roomConnections.delete(connection);
			if (roomConnections.size === 0) this.connections.delete(room.id);
		});
		ws.on("error", () => {
			// Closing sockets can race with a broadcast during network changes.
		});

		const currentRoom = await this.service.getRoom(userId, room.id).catch(() => null);
		const currentMember = currentRoom?.members.find((item) => item.userId === userId && item.status === "accepted");
		if (!currentRoom || !currentMember) {
			roomConnections.delete(connection);
			if (roomConnections.size === 0) this.connections.delete(room.id);
			ws.close(1008, "room_access_denied");
			return;
		}

		send(ws, { type: "room_snapshot", room: currentRoom });
		const latestMatch = currentRoom.latestMatch?.id ?? null;
		if (latestMatch) {
			const snapshot = await this.service.getMatchSnapshot(userId, latestMatch).catch(() => null);
			if (snapshot) send(ws, { type: "match_snapshot", match: snapshot });
		}

		ws.on("message", (raw) => {
			void this.handleMessage(connection, raw.toString());
		});
	}

	private async handleMessage(connection: Connection, raw: string) {
		let message: any;
		try {
			message = JSON.parse(raw);
		} catch {
			send(connection.ws, { type: "action_rejected", code: "invalid_json" });
			return;
		}
		if (message?.type === "resume") {
			const resume = z.object({ type: z.literal("resume"), matchId: z.uuid(), lastRevision: z.number().int().nonnegative().optional() }).safeParse(message);
			if (!resume.success) {
				send(connection.ws, { type: "action_rejected", code: "invalid_action", message: "Invalid resume request." });
				return;
			}
			const matchId = resume.data.matchId;
			if (matchId) {
				if (!(await this.matchBelongsToRoom(matchId, connection.roomId))) {
					send(connection.ws, { type: "action_rejected", code: "room_mismatch" });
					return;
				}
				const snapshot = await this.service.getMatchSnapshot(connection.userId, matchId).catch(() => null);
				if (snapshot) send(connection.ws, { type: "match_snapshot", match: snapshot });
			}
			return;
		}
		if (message?.type === "leave_match") {
			const leave = z.object({ type: z.literal("leave_match"), matchId: z.uuid() }).safeParse(message);
			if (!leave.success) {
				send(connection.ws, { type: "action_rejected", code: "invalid_action" });
				return;
			}
			try {
				if (!(await this.matchBelongsToRoom(leave.data.matchId, connection.roomId))) {
					send(connection.ws, { type: "action_rejected", code: "room_mismatch" });
					return;
				}
				const result = await this.service.leaveMatch(connection.userId, leave.data.matchId);
				send(connection.ws, { type: "action_accepted", action: { kind: "leave_match", ...result } });
				await this.broadcastMatch(leave.data.matchId);
			} catch (error) {
				send(connection.ws, {
					type: "action_rejected",
					code: error instanceof Error && "code" in error ? (error as Error & { code?: string }).code : "action_failed",
					message: error instanceof Error ? error.message : "Action failed.",
				});
			}
			return;
		}
		const isDirectAction = ["open_character", "guess_song", "buy_hint"].includes(message?.type);
		const parsed = isDirectAction ? directActionMessageSchema.safeParse(message) : actionMessageSchema.safeParse(message);
		if (!parsed.success) {
			send(connection.ws, { type: "action_rejected", code: "invalid_action" });
			return;
		}
		const data = parsed.data;
		const actionId = data.actionId ?? randomUUID();
		const payloadResult = isDirectAction
			? actionPayloadSchema.safeParse({ ...(data.payload ?? message), kind: data.type })
			: { success: true as const, data: data.payload };
		if (!payloadResult.success) {
			send(connection.ws, { type: "action_rejected", code: "invalid_action" });
			return;
		}
		const payload = payloadResult.data as LetterGameActionInput;
		try {
			if (!(await this.matchBelongsToRoom(data.matchId, connection.roomId))) {
				send(connection.ws, { type: "action_rejected", code: "room_mismatch" });
				return;
			}
			const result = await this.service.applyAction(
				connection.userId,
				data.matchId,
				actionId,
				data.expectedRevision,
				payload,
			);
			send(connection.ws, { type: "action_accepted", action: result });
			await this.broadcastMatch(data.matchId);
		} catch (error) {
			send(connection.ws, {
				type: "action_rejected",
				code: error instanceof Error && "code" in error ? (error as Error & { code?: string }).code : "action_failed",
				message: error instanceof Error ? error.message : "Action failed.",
			});
		}
	}

	async broadcastMatch(matchId: string) {
		const roomId = await this.service.getMatchRoomId(matchId);
		if (!roomId) return;
		const connections = [...this.connections.values()]
			.flatMap((items) => [...items])
			.filter((connection) => connection.roomId === roomId);
		await Promise.all(
			connections.map(async (connection) => {
				const snapshot = await this.service.getMatchSnapshot(connection.userId, matchId).catch(() => null);
				if (snapshot) send(connection.ws, { type: "match_snapshot", match: snapshot });
			}),
		);
	}

	private async matchBelongsToRoom(matchId: string, roomId: string) {
		return (await this.service.getMatchRoomId(matchId)) === roomId;
	}
}
