/* eslint-disable @typescript-eslint/no-explicit-any */
import type { IncomingMessage, Server } from "node:http";
import { randomUUID } from "node:crypto";
import { WebSocketServer, WebSocket } from "ws";
import { container, injectable } from "tsyringe";
import { JwtService } from "./jwt.service.js";
import { LetterGameService } from "./letter-game.service.js";

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
				if (room) send(connection.ws, { type: "room_snapshot", room });
			}),
		);
	}

	private async handleConnection(ws: WebSocket, request: IncomingMessage, roomCode: string) {
		const authorization = request.headers.authorization;
		if (!authorization?.startsWith("Bearer ")) {
			unauthorized(request.socket);
			return;
		}
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

		send(ws, { type: "room_snapshot", room });
		const activeMatch = room.latestMatch?.status === "active" ? room.latestMatch.id : null;
		if (activeMatch) {
			const snapshot = await this.service.getMatchSnapshot(userId, activeMatch).catch(() => null);
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
			const matchId = typeof message.matchId === "string" ? message.matchId : null;
			if (matchId) {
				const snapshot = await this.service.getMatchSnapshot(connection.userId, matchId).catch(() => null);
				if (snapshot) send(connection.ws, { type: "match_snapshot", match: snapshot });
			}
			return;
		}
		if (message?.type === "leave_match") {
			if (typeof message.matchId !== "string") {
				send(connection.ws, { type: "action_rejected", code: "invalid_action" });
				return;
			}
			try {
				const result = await this.service.leaveMatch(connection.userId, message.matchId);
				send(connection.ws, { type: "action_accepted", action: { kind: "leave_match", ...result } });
				await this.broadcastMatch(message.matchId);
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
		if ((!isDirectAction && message?.type !== "action") || typeof message.matchId !== "string") {
			send(connection.ws, { type: "action_rejected", code: "invalid_action" });
			return;
		}
		const actionId = typeof message.actionId === "string" ? message.actionId : randomUUID();
		const payload = isDirectAction
			? { ...(message.payload && typeof message.payload === "object" ? message.payload : message), kind: message.type }
			: message.payload;
		if (!payload || typeof payload !== "object") {
			send(connection.ws, { type: "action_rejected", code: "invalid_action" });
			return;
		}
		try {
			const result = await this.service.applyAction(
				connection.userId,
				message.matchId,
				actionId,
				typeof message.expectedRevision === "number" ? message.expectedRevision : undefined,
				payload,
			);
			send(connection.ws, { type: "action_accepted", action: result });
			await this.broadcastMatch(message.matchId);
		} catch (error) {
			send(connection.ws, {
				type: "action_rejected",
				code: error instanceof Error && "code" in error ? (error as Error & { code?: string }).code : "action_failed",
				message: error instanceof Error ? error.message : "Action failed.",
			});
		}
	}

	private async broadcastMatch(matchId: string) {
		const connections = [...this.connections.values()]
			.flatMap((items) => [...items])
			.filter((connection) => this.connections.has(connection.roomId));
		await Promise.all(
			connections.map(async (connection) => {
				const snapshot = await this.service.getMatchSnapshot(connection.userId, matchId).catch(() => null);
				if (snapshot) send(connection.ws, { type: "match_snapshot", match: snapshot });
			}),
		);
	}
}
