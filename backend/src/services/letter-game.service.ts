/* eslint-disable @typescript-eslint/no-explicit-any */
import { randomInt, randomUUID } from "node:crypto";
import { inject, injectable } from "tsyringe";
import type { PrismaClient } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";
import { AppError } from "../lib/errors.js";
import { CatalogService } from "./catalog.service.js";
import {
	buildLetterTokens,
	guessScore,
	guessSongMatches,
	maskLetterTokens,
	remainingCharacterCount,
	revealCharacter,
	segmentLetterText,
} from "./letter-game.rules.js";

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const DEFAULT_TURN_SECONDS = 30;
const DEFAULT_STALLED_ROUNDS = 3;
const DEFAULT_PUBLIC_HINT_COST = 5;
const DEFAULT_PRIVATE_HINT_COST = 10;
const MEMBER_STALE_AFTER_MS = 2 * 60 * 1000;

type PrismaLike = PrismaClient & Record<string, any>;

export type LetterGameRoomSettingsInput = {
	visibility: "public" | "private";
	hostMode?: "fixed" | "rotate";
	turnDurationSeconds?: number;
	stalledRoundLimit?: number;
	songCount?: number | null | undefined;
	publicHintCost?: number;
	privateHintCost?: number;
	selectionMode?: "filtered_random" | "collection" | undefined;
	selectionConfig?: Record<string, unknown>;
};

export type LetterGameActionInput =
	| { kind: "open_character"; character: string }
	| { kind: "guess_song"; slotId: string; guess: string }
	| {
			kind: "buy_hint";
			slotId: string;
			hintType: "version" | "constant" | "white_chart";
			visibility: "public" | "private";
			difficulty?: string;
	  };

const intInRange = (value: number | undefined, fallback: number, min: number, max: number): number => {
	if (value === undefined) return fallback;
	if (!Number.isInteger(value) || value < min || value > max) {
		throw new AppError(400, "invalid_room_settings", `Value must be an integer between ${min} and ${max}.`);
	}
	return value;
};

const makeRoomCode = (): string => {
	let value = "";
	for (let index = 0; index < 6; index += 1) {
		value += CODE_ALPHABET[randomInt(CODE_ALPHABET.length)];
	}
	return value;
};

const shuffle = <T>(values: T[]): T[] => {
	const result = [...values];
	for (let index = result.length - 1; index > 0; index -= 1) {
		const swapIndex = randomInt(index + 1);
		[result[index], result[swapIndex]] = [result[swapIndex]!, result[index]!];
	}
	return result;
};

const jsonArray = <T>(value: unknown): T[] => (Array.isArray(value) ? (value as T[]) : []);

const jsonObject = (value: unknown): Record<string, unknown> =>
	typeof value === "object" && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : {};

const jsonString = (value: unknown): string | null => {
	if (typeof value === "string") return value;
	if (typeof value === "number" || typeof value === "boolean") return String(value);
	return null;
};

const jsonNumber = (value: unknown): number | null =>
	typeof value === "number" && Number.isFinite(value) ? value : null;

const isUtageChartType = (value: unknown): boolean => /utage|宴/iu.test(String(value));
const isEnglishOnlyTitle = (value: unknown): boolean => {
	const title = String(value);
	return title.length > 0 && /^[\x20-\x7E]+$/u.test(title);
};

const normalizeSourceIds = (value: unknown): string[] =>
	Array.isArray(value)
		? [
				...new Set(
					value
						.filter((item): item is string => typeof item === "string")
						.map((item) => item.trim())
						.filter(Boolean),
				),
			]
		: [];

@injectable()
export class LetterGameService {
	private readonly prisma: PrismaLike;

	constructor(
		@inject(TOKENS.Prisma) prisma: PrismaClient,
		@inject(CatalogService) private readonly catalogService: CatalogService,
	) {
		this.prisma = prisma as PrismaLike;
	}

	async createRoom(userId: string, input: LetterGameRoomSettingsInput) {
		const settings = this.validateSettings(input);
		if (settings.selectionMode === "collection") {
			const songIds = await this.collectionSongIds(userId, settings.selectionConfig);
			if (songIds.length < 1 || songIds.length > 5000) {
				throw new AppError(400, "invalid_collection_song_count", "Collections must contain between 1 and 5000 unique songs.");
			}
			settings.songCountOverride = songIds.length;
		}
		for (let attempt = 0; attempt < 5; attempt += 1) {
			const code = makeRoomCode();
			try {
				const room = await this.prisma.letterGameRoom.create({
					data: {
						code,
						visibility: settings.visibility,
						hostMode: settings.hostMode,
						hostUserId: userId,
						turnDurationSeconds: settings.turnDurationSeconds,
						stalledRoundLimit: settings.stalledRoundLimit,
						songCountOverride: settings.songCountOverride,
						publicHintCost: settings.publicHintCost,
						privateHintCost: settings.privateHintCost,
						selectionMode: settings.selectionMode,
						selectionConfig: settings.selectionConfig as any,
						members: {
							create: { userId, seatOrder: 0, status: "accepted", approvedAt: new Date(), lastSeenAt: new Date() },
						},
					},
				});
				return this.serializeRoom(room, [{ userId, status: "accepted", seatOrder: 0 }]);
			} catch (error) {
				if (attempt === 4 || !(error instanceof Error && /unique/i.test(error.message))) throw error;
			}
		}
		throw new AppError(503, "room_code_unavailable", "Unable to allocate a room code.");
	}

	async listPublicRooms() {
		const rooms = await this.prisma.letterGameRoom.findMany({
			where: { visibility: "public", status: "open", members: { some: { status: "accepted" } } },
			orderBy: { updatedAt: "desc" },
			take: 100,
			include: { members: { where: { status: "accepted" }, select: { id: true, userId: true, status: true, seatOrder: true } } },
		});
		return Promise.all(rooms.map((room: any) => this.serializeRoom(room, room.members)));
	}

	async getRoom(userId: string, roomIdOrCode: string) {
		const room = await this.prisma.letterGameRoom.findFirst({
			where: roomIdOrCode.length === 36 ? { id: roomIdOrCode } : { code: roomIdOrCode.toUpperCase() },
			include: {
				members: { orderBy: { seatOrder: "asc" }, select: { id: true, userId: true, status: true, seatOrder: true, joinedAt: true } },
				matches: { orderBy: { sequence: "desc" }, take: 1, select: { id: true, sequence: true, status: true, revision: true } },
			},
		});
		if (!room) throw new AppError(404, "room_not_found", "Letter game room not found.");
		if (room.status !== "open") throw new AppError(410, "room_closed", "This letter game room has been dissolved.");
		const member = room.members.find((item: any) => item.userId === userId && ["accepted", "pending"].includes(item.status));
		if (room.visibility === "private" && !member) throw new AppError(403, "room_access_denied", "Join the private room first.");
		return this.serializeRoom(room, room.members);
	}

	/** Refreshes room presence while a client keeps its WebSocket alive. */
	async touchMember(userId: string, roomId: string) {
		await this.prisma.letterGameRoomMember.updateMany({
			where: { roomId, userId, status: "accepted" },
			data: { lastSeenAt: new Date() },
		});
	}

	async joinRoom(userId: string, code: string) {
		const normalizedCode = code.trim().toUpperCase();
		const roomId = await this.prisma.$transaction(async (tx: any) => {
			const roomLocation = await tx.letterGameRoom.findUnique({ where: { code: normalizedCode }, select: { id: true } });
			if (!roomLocation) throw new AppError(404, "room_not_found", "Letter game room not found.");
			await tx.$executeRawUnsafe("SELECT pg_advisory_xact_lock(hashtextextended($1, 0))", roomLocation.id);
			const room = await tx.letterGameRoom.findUnique({ where: { id: roomLocation.id } });
			if (!room) throw new AppError(404, "room_not_found", "Letter game room not found.");
			if (room.status !== "open") throw new AppError(410, "room_closed", "This letter game room has been dissolved.");
			const existing = await tx.letterGameRoomMember.findUnique({
				where: { roomId_userId: { roomId: room.id, userId } },
			});
			if (existing?.status === "kicked") throw new AppError(403, "member_kicked", "You were removed from this room.");
			if (existing && ["accepted", "pending"].includes(existing.status)) return room.id;
			const activeMatch = await tx.letterGameMatch.findFirst({ where: { roomId: room.id, status: "active" }, select: { id: true } });
			if (activeMatch) throw new AppError(409, "match_active", "This room cannot accept new players during a match.");
			// Private rooms accept a code join immediately. Public rooms wait for host approval.
			const accepted = room.visibility === "private";
			const lastSeat = await tx.letterGameRoomMember.findFirst({
				where: { roomId: room.id },
				orderBy: { seatOrder: "desc" },
				select: { seatOrder: true },
			});
			await tx.letterGameRoomMember.upsert({
				where: { roomId_userId: { roomId: room.id, userId } },
				create: {
					roomId: room.id,
					userId,
					seatOrder: (lastSeat?.seatOrder ?? -1) + 1,
					status: accepted ? "accepted" : "pending",
					approvedAt: accepted ? new Date() : null,
					lastSeenAt: new Date(),
				},
				update: {
					status: accepted ? "accepted" : "pending",
					leftAt: null,
					lastSeenAt: new Date(),
					approvedAt: accepted ? new Date() : null,
				},
			});
			return room.id;
		});
		return this.getRoom(userId, roomId);
	}

	async approveMember(actorId: string, roomId: string, memberId: string) {
		const member = await this.prisma.$transaction(async (tx: any) => {
			await tx.$executeRawUnsafe("SELECT pg_advisory_xact_lock(hashtextextended($1, 0))", roomId);
			const room = await tx.letterGameRoom.findUnique({ where: { id: roomId } });
			if (!room) throw new AppError(404, "room_not_found", "Letter game room not found.");
			if (room.status !== "open") throw new AppError(410, "room_closed", "This letter game room has been dissolved.");
			if (room.hostUserId !== actorId) throw new AppError(403, "host_required", "Only the room host can perform this action.");
			const activeMatch = await tx.letterGameMatch.findFirst({ where: { roomId, status: "active" }, select: { id: true } });
			if (activeMatch) throw new AppError(409, "match_active", "Members cannot be approved during an active match.");
			const pendingMember = await tx.letterGameRoomMember.findFirst({ where: { id: memberId, roomId, status: "pending" } });
			if (!pendingMember) throw new AppError(409, "member_not_pending", "This member is no longer waiting for approval.");
			return tx.letterGameRoomMember.update({
				where: { id: memberId },
				data: { status: "accepted", approvedAt: new Date(), leftAt: null },
			});
		});
		return { room: await this.getRoom(actorId, roomId), member };
	}

	async updateSettings(actorId: string, roomId: string, input: Partial<LetterGameRoomSettingsInput>) {
		const room = await this.requireHost(actorId, roomId);
		const active = await this.prisma.letterGameMatch.findFirst({ where: { roomId, status: "active" }, select: { id: true } });
		if (active) throw new AppError(409, "match_active", "Room settings can only change between matches.");
		const settings = this.validateSettings({
			visibility: room.visibility,
			hostMode: input.hostMode ?? room.hostMode,
			turnDurationSeconds: input.turnDurationSeconds ?? room.turnDurationSeconds,
			stalledRoundLimit: input.stalledRoundLimit ?? room.stalledRoundLimit,
			songCount: input.songCount === undefined ? room.songCountOverride : input.songCount,
			publicHintCost: room.visibility === "public" ? room.publicHintCost : (input.publicHintCost ?? room.publicHintCost),
			privateHintCost: room.visibility === "public" ? room.privateHintCost : (input.privateHintCost ?? room.privateHintCost),
			selectionMode: (input.selectionMode ?? room.selectionMode) as LetterGameRoomSettingsInput["selectionMode"],
			selectionConfig: input.selectionConfig ?? jsonObject(room.selectionConfig),
		});
		const acceptedMemberCount = await this.prisma.letterGameRoomMember.count({ where: { roomId, status: "accepted" } });
		if (settings.selectionMode === "collection") {
			const songIds = await this.collectionSongIds(actorId, settings.selectionConfig);
			if (songIds.length > 5000) {
				throw new AppError(
					400,
					"invalid_collection_song_count",
					"Collections can contain at most 5000 unique songs.",
				);
			}
			settings.songCountOverride = songIds.length;
		} else if (settings.songCountOverride !== null && settings.songCountOverride < acceptedMemberCount) {
			throw new AppError(400, "song_count_too_low", "Song count must be at least the number of accepted players.");
		}
		await this.prisma.letterGameRoom.update({
			where: { id: roomId },
			data: {
				hostMode: settings.hostMode,
				turnDurationSeconds: settings.turnDurationSeconds,
				stalledRoundLimit: settings.stalledRoundLimit,
				songCountOverride: settings.songCountOverride,
				publicHintCost: settings.publicHintCost,
				privateHintCost: settings.privateHintCost,
				selectionMode: settings.selectionMode,
				selectionConfig: settings.selectionConfig as any,
			},
		});
		return this.getRoom(actorId, roomId);
	}

	async kickMember(actorId: string, roomId: string, memberId: string) {
		await this.requireHost(actorId, roomId);
		const member = await this.prisma.letterGameRoomMember.findFirst({ where: { id: memberId, roomId } });
		if (!member) throw new AppError(404, "member_not_found", "Room member not found.");
		if (member.userId === actorId) throw new AppError(400, "cannot_kick_host", "The host cannot kick themselves.");
		const activeMatch = await this.prisma.letterGameMatch.findFirst({
			where: { roomId, status: "active" },
			include: { players: true },
		});
		if (activeMatch?.players.some((player: any) => player.userId === member.userId && player.status === "active")) {
			await this.leaveMatch(member.userId, activeMatch.id);
		}
		await this.prisma.letterGameRoomMember.update({
			where: { id: memberId },
			data: { status: "kicked", kickedAt: new Date(), leftAt: new Date() },
		});
		return this.getRoom(actorId, roomId);
	}

	async leaveRoom(userId: string, roomId: string) {
		return this.leaveRoomMember(userId, roomId);
	}

	async leaveFinishedRoomOnDisconnect(userId: string, roomId: string) {
		return this.leaveRoomMember(userId, roomId, { requireFinishedMatch: true });
	}

	private async leaveRoomMember(
		userId: string,
		roomId: string,
		options: { staleBefore?: Date; requireFinishedMatch?: boolean } = {},
	) {
		return this.prisma.$transaction(async (tx: any) => {
			await tx.$executeRawUnsafe("SELECT pg_advisory_xact_lock(hashtextextended($1, 0))", roomId);
			const room = await tx.letterGameRoom.findUnique({ where: { id: roomId } });
			if (!room) throw new AppError(404, "room_not_found", "Letter game room not found.");
			const member = await tx.letterGameRoomMember.findUnique({ where: { roomId_userId: { roomId, userId } } });
			if (!member) throw new AppError(404, "member_not_found", "Room member not found.");
			if (room.status !== "open") return { left: true, dissolved: true };
			if (options.staleBefore && member.lastSeenAt && member.lastSeenAt >= options.staleBefore) {
				return { left: false, dissolved: false };
			}

			const now = new Date();
			const activeMatch = await tx.letterGameMatch.findFirst({
				where: { roomId, status: "active" },
				include: { players: true },
			});
			if (options.requireFinishedMatch) {
				if (activeMatch) return { left: false, dissolved: false };
				const latestMatch = await tx.letterGameMatch.findFirst({
					where: { roomId },
					orderBy: { sequence: "desc" },
					select: { status: true },
				});
				if (!latestMatch || !["finished", "abandoned"].includes(latestMatch.status)) {
					return { left: false, dissolved: false };
				}
			}
			const player = activeMatch?.players.find((item: any) => item.userId === userId && item.status === "active");
			if (activeMatch && player) {
				await tx.letterGameMatchPlayer.update({ where: { id: player.id }, data: { status: "left" } });
				const players = activeMatch.players.map((item: any) => (item.id === player.id ? { ...item, status: "left" } : item));
				const order = jsonArray<string>(activeMatch.turnOrder);
				const isCurrentTurn = order[activeMatch.currentTurnIndex] === userId;
				const next = isCurrentTurn
					? this.nextTurn(order, activeMatch.currentTurnIndex, players)
					: { index: activeMatch.currentTurnIndex, userId: order[activeMatch.currentTurnIndex] ?? null };
				const finished = !players.some((item: any) => item.status === "active") || !next.userId;
				await tx.letterGameMatch.update({
					where: { id: activeMatch.id },
					data: {
						revision: { increment: 1 },
						currentTurnIndex: next.index,
						turnDeadline: finished ? null : new Date(now.getTime() + room.turnDurationSeconds * 1000),
						...(finished ? { status: "abandoned", endedAt: now } : {}),
					},
				});
			}

			if (["accepted", "pending"].includes(member.status)) {
				await tx.letterGameRoomMember.update({ where: { id: member.id }, data: { status: "left", leftAt: now } });
			}
			const dissolved = await this.closeRoomIfEmpty(tx, roomId, now);
			if (dissolved) return { left: true, dissolved: true };

			if (room.hostUserId === userId && member.status === "accepted") {
				const successor = await tx.letterGameRoomMember.findFirst({
					where: { roomId, status: "accepted", userId: { not: userId } },
					orderBy: { seatOrder: "asc" },
				});
				if (successor) await tx.letterGameRoom.update({ where: { id: roomId }, data: { hostUserId: successor.userId } });
			}
			return { left: true, dissolved: false };
		});
	}

	async startMatch(actorId: string, roomId: string) {
		const matchId = await this.prisma.$transaction(async (tx: any) => {
			await tx.$executeRawUnsafe("SELECT pg_advisory_xact_lock(hashtextextended($1, 0))", roomId);
			const room = await tx.letterGameRoom.findUnique({ where: { id: roomId } });
			if (!room) throw new AppError(404, "room_not_found", "Letter game room not found.");
			if (room.status !== "open") throw new AppError(410, "room_closed", "This letter game room has been dissolved.");
			if (room.hostUserId !== actorId) throw new AppError(403, "host_required", "Only the room host can perform this action.");
			const members = await tx.letterGameRoomMember.findMany({
				where: { roomId, status: "accepted" },
				orderBy: { seatOrder: "asc" },
			});
			if (members.length < 1) throw new AppError(400, "no_players", "At least one accepted player is required.");
			const latest = await tx.letterGameMatch.findFirst({
				where: { roomId },
				orderBy: { sequence: "desc" },
				select: { sequence: true, status: true },
			});
			if (latest?.status === "active") throw new AppError(409, "match_active", "This room already has an active match.");
			const songs = await this.selectSongs(actorId, room, tx);
			const targetCount = room.songCountOverride ?? members.length * 3;
			if (targetCount < members.length)
				throw new AppError(400, "song_count_too_low", "Song count must be at least the number of players.");
			if (songs.length < targetCount)
				throw new AppError(400, "not_enough_songs", "The selected source does not contain enough unique songs.");

			const selectedSongs = songs.slice(0, targetCount);
			const turnOrder = shuffle<string>(members.map((member: any) => String(member.userId)));
			const now = new Date();
			const match = await tx.letterGameMatch.create({
				data: {
					roomId,
					sequence: (latest?.sequence ?? 0) + 1,
					sourceType: room.selectionMode,
					sourceConfig: room.selectionConfig as any,
					hostUserId: room.hostUserId,
					turnOrder,
					turnDeadline: new Date(now.getTime() + room.turnDurationSeconds * 1000),
					players: {
						create: turnOrder.map((userId: string, turnOrderIndex: number) => ({
							userId,
							turnOrder: turnOrderIndex,
							scoringEligible: room.selectionMode !== "collection" || userId !== room.hostUserId,
						})),
					},
					songs: {
						create: selectedSongs.map((song: any) => {
							const hasRevealableCharacters = buildLetterTokens(song.title).some((token) => token.value !== " ");
							return {
								slotId: randomUUID(),
								songIdentifier: song.songIdentifier,
								title: song.title,
								aliases: song.aliases,
								...(hasRevealableCharacters
									? {}
									: { status: "completed", completionReason: "all_characters_revealed", completedAt: now }),
							};
						}),
					},
				},
			});
			if (!selectedSongs.some((song: any) => buildLetterTokens(song.title).some((token) => token.value !== " " ))) {
				await tx.letterGameMatch.update({
					where: { id: match.id },
					data: { status: "finished", endedAt: now, turnDeadline: null },
				});
			}
			return match.id;
		});
		return this.getMatchSnapshot(actorId, matchId);
	}

	async prepareReopen(actorId: string, roomId: string) {
		await this.requireHost(actorId, roomId);
		const active = await this.prisma.letterGameMatch.findFirst({ where: { roomId, status: "active" }, select: { id: true } });
		if (active) throw new AppError(409, "match_active", "This room already has an active match.");
		const room = await this.prisma.letterGameRoom.findUnique({ where: { id: roomId }, select: { hostMode: true, hostUserId: true } });
		let nextHostUserId = room?.hostUserId ?? actorId;
		if (room?.hostMode === "rotate") {
			const accepted = await this.prisma.letterGameRoomMember.findMany({
				where: { roomId, status: "accepted" },
				orderBy: { seatOrder: "asc" },
				select: { userId: true },
			});
			const currentIndex = accepted.findIndex((member: any) => member.userId === room.hostUserId);
			const nextHost = accepted[(currentIndex + 1 + accepted.length) % accepted.length];
			if (nextHost) {
				nextHostUserId = nextHost.userId;
			}
			if (nextHostUserId !== room.hostUserId)
				await this.prisma.letterGameRoom.update({ where: { id: roomId }, data: { hostUserId: nextHostUserId } });
		}
		await this.prisma.letterGameRoomMember.updateMany({
			where: { roomId, userId: { not: nextHostUserId }, status: "accepted" },
			data: { status: "pending", approvedAt: null },
		});
		return this.getRoom(actorId, roomId);
	}

	async getMatchSnapshot(userId: string, matchId: string) {
		const match = await this.prisma.letterGameMatch.findUnique({
			where: { id: matchId },
			include: {
				players: { orderBy: { turnOrder: "asc" } },
				songs: { orderBy: { createdAt: "asc" } },
				facts: true,
				room: { select: { code: true } },
				actions: { orderBy: { sequence: "desc" }, take: 80 },
			},
		});
		if (!match) throw new AppError(404, "match_not_found", "Letter game match not found.");
		const player = match.players.find((item: any) => item.userId === userId);
		if (!player || (match.status === "active" && player.status !== "active"))
			throw new AppError(403, "match_access_denied", "You are not an active player in this match.");
		const publicFacts = match.facts.filter((fact: any) => fact.visibility === "public");
		const userIds = [...new Set(match.players.map((item: any) => item.userId))];
		const [profiles, users] = await Promise.all([
			this.prisma.profile.findMany({
				where: { userId: { in: userIds }, isActive: true },
				select: { userId: true, avatarUrl: true },
			}),
			this.prisma.user.findMany({
				where: { id: { in: userIds } },
				select: { id: true, username: true },
			}),
		]);
		const profileByUser = new Map(profiles.map((profile: any) => [profile.userId, profile]));
		const handleByUser = new Map(
			users.map((user: any) => [user.id, user.username]),
		);
		const songIdentifiers = match.songs.map((song: any) => song.songIdentifier);
		const [catalogSongs, sheets] = await Promise.all([
			this.prisma.song.findMany({
				where: { songIdentifier: { in: songIdentifiers } },
				select: { songIdentifier: true, imageName: true, artist: true, version: true },
			}),
			this.prisma.sheet.findMany({
				where: { songIdentifier: { in: songIdentifiers }, disabled: false },
				select: { songIdentifier: true, chartType: true, difficulty: true, levelValue: true, internalLevelValue: true },
			}),
		]);
		const songByIdentifier = new Map(catalogSongs.map((song: any) => [song.songIdentifier, song]));
		const sheetsByIdentifier = new Map<string, any[]>();
		for (const sheet of sheets) sheetsByIdentifier.set(sheet.songIdentifier, [...(sheetsByIdentifier.get(sheet.songIdentifier) ?? []), sheet]);
		const songNumberById = new Map(match.songs.map((song: any, index: number) => [song.id, index + 1]));
		const logs = match.actions
			.slice()
			.reverse()
			.filter((action: any) => {
				if (action.actionType !== "buy_hint") return true;
				const payload = jsonObject(action.payload);
				return payload.visibility !== "private" || action.actorId === userId;
			})
			.map((action: any) => {
				const actorName = handleByUser.get(action.actorId) ?? action.actorId;
				const payload = jsonObject(action.payload);
				const result = jsonObject(action.result);
				const hint = jsonObject(result.hint);
				const kind = action.actionType;
				let message = kind;
				if (kind === "open_character") message = `${actorName} opened ${String(payload.character ?? "?")} (${String(result.newlyRevealedCount ?? 0)} revealed, +${String(result.points ?? 0)} points)`;
				if (kind === "guess_song") message = result.correct ? `${actorName} guessed a song (+${String(result.points ?? 0)})` : `${actorName} made an incorrect guess`;
				if (kind === "buy_hint") {
					const songNumber = songNumberById.get(action.songId) ?? (typeof payload.slotId === "string" ? match.songs.findIndex((song: any) => song.slotId === payload.slotId) + 1 : 0);
					const detail = hint.value === undefined ? "" : `: ${JSON.stringify(hint.value)}`;
					message = `${actorName} spent ${String(hint.cost ?? 0)} points on song #${songNumber} ${String(hint.type ?? "hint")} hint${detail}`;
				}
				return {
					id: action.id,
					message,
					actorUserId: action.actorId,
					actorName,
					actionType: kind,
					character: kind === "open_character" ? jsonString(payload.character) : null,
					newlyRevealedCount: kind === "open_character" ? jsonNumber(result.newlyRevealedCount) : null,
					points: kind === "open_character" || kind === "guess_song" ? jsonNumber(result.points) : jsonNumber(hint.cost),
					correct: kind === "guess_song" && typeof result.correct === "boolean" ? result.correct : null,
					hintType: kind === "buy_hint" ? jsonString(hint.type) : null,
					hintCost: kind === "buy_hint" ? jsonNumber(hint.cost) : null,
					songNumber: kind === "buy_hint" ? songNumberById.get(action.songId) ?? (typeof payload.slotId === "string" ? match.songs.findIndex((song: any) => song.slotId === payload.slotId) + 1 : null) : null,
				};
			});
		return {
			matchId: match.id,
			roomCode: match.room.code,
			status: match.status,
			revision: match.revision,
			turnUserId: jsonArray<string>(match.turnOrder)[match.currentTurnIndex] ?? null,
			turnDeadline: match.turnDeadline,
			noProgressRounds: match.noProgressRounds,
			players: match.players.map((item: any) => ({
				userId: item.userId,
				score: item.score,
				turnOrder: item.turnOrder,
				status: item.status,
				scoringEligible: item.scoringEligible,
				displayName: handleByUser.get(item.userId) ?? null,
				avatarUrl: profileByUser.get(item.userId)?.avatarUrl ?? null,
			})),
				songs: match.songs.map((song: any) => {
				const revealedIndices = jsonArray<number>(song.revealedIndices);
				const tokens = buildLetterTokens(song.title, revealedIndices);
				const completed = song.status === "completed";
				const catalogSong = songByIdentifier.get(song.songIdentifier);
				const songSheets = sheetsByIdentifier.get(song.songIdentifier) ?? [];
				const publicWhiteFact = publicFacts.find((fact: any) => fact.songId === song.id && fact.factType === "white_chart");
				const privateWhiteFact = match.facts.find((fact: any) => fact.songId === song.id && fact.userId === userId && fact.factType === "white_chart");
					const showFullDetails = match.status !== "active" || completed;
				const shownVersionFact = [...publicFacts, ...match.facts.filter((fact: any) => fact.userId === userId)].find((fact: any) => fact.songId === song.id && fact.factType === "version");
				const masterSheet = songSheets.find((sheet: any) => sheet.difficulty.toLowerCase() === "master");
				const remasterSheet = songSheets.find((sheet: any) => /remaster/iu.test(sheet.difficulty));
				return {
					slotId: song.slotId,
						title: showFullDetails ? song.title : maskLetterTokens(tokens),
						remainingCharacterCount: showFullDetails ? 0 : remainingCharacterCount(tokens),
					status: song.status,
					completionReason: song.completionReason,
					completedByUserId: song.completedByUserId,
					facts: [...publicFacts, ...match.facts.filter((fact: any) => fact.userId === userId && fact.songId === song.id)]
						.filter((fact: any) => fact.songId === song.id)
						.map((fact: any) => ({ type: fact.factType, visibility: fact.visibility, value: fact.value })),
					imageName: showFullDetails ? catalogSong?.imageName ?? null : null,
					artist: showFullDetails ? catalogSong?.artist ?? null : null,
					version: showFullDetails ? catalogSong?.version ?? null : jsonString(shownVersionFact?.value),
					chartTypes: showFullDetails
						? [...new Set(songSheets.map((sheet: any) => String(sheet.chartType)).filter((type: string) => !isUtageChartType(type)))]
						: [],
						hasRemaster: publicWhiteFact?.value === true || privateWhiteFact?.value === true,
					masterConstant: showFullDetails && masterSheet ? String(masterSheet.internalLevelValue ?? masterSheet.levelValue ?? "") : null,
					remasterConstant: showFullDetails && remasterSheet ? String(remasterSheet.internalLevelValue ?? remasterSheet.levelValue ?? "") : null,
				};
			}),
			logs,
		};
	}

	async getMatchRoomId(matchId: string) {
		const match = await this.prisma.letterGameMatch.findUnique({ where: { id: matchId }, select: { roomId: true } });
		return match?.roomId ?? null;
	}

	async applyAction(
		userId: string,
		matchId: string,
		idempotencyKey: string,
		expectedRevision: number | undefined,
		input: LetterGameActionInput,
	) {
		if (!idempotencyKey.trim()) throw new AppError(400, "idempotency_required", "An action idempotency key is required.");
		if (input.kind === "open_character" && segmentLetterText(input.character).length !== 1)
			throw new AppError(400, "invalid_character", "Exactly one character is required.");
		const result = await this.prisma.$transaction(async (tx: any) => {
			const matchLocation = await tx.letterGameMatch.findUnique({ where: { id: matchId }, select: { roomId: true } });
			if (!matchLocation) throw new AppError(404, "match_not_found", "Letter game match not found.");
			await tx.$executeRawUnsafe("SELECT pg_advisory_xact_lock(hashtextextended($1, 0))", matchLocation.roomId);
			const match = await tx.letterGameMatch.findUnique({ where: { id: matchId }, include: { players: true, songs: true } });
			if (!match) throw new AppError(404, "match_not_found", "Letter game match not found.");
			const room = await tx.letterGameRoom.findUnique({ where: { id: match.roomId } });
			if (!room) throw new AppError(404, "room_not_found", "Letter game room not found.");
			if (room.status !== "open") throw new AppError(410, "room_closed", "This letter game room has been dissolved.");
			const existing = await tx.letterGameAction.findFirst({ where: { matchId, actorId: userId, idempotencyKey } });
			if (existing) return existing.result;
			if (match.status !== "active") throw new AppError(409, "match_finished", "This match has ended.");
			if (expectedRevision !== undefined && expectedRevision !== match.revision)
				throw new AppError(409, "stale_revision", "The match state has changed. Refresh and retry.");
			const players = match.players as any[];
			const actor = players.find((player) => player.userId === userId && player.status === "active");
			if (!actor) throw new AppError(403, "player_inactive", "You are not an active player in this match.");
			const order = jsonArray<string>(match.turnOrder);
			if (order[match.currentTurnIndex] !== userId) throw new AppError(409, "not_your_turn", "It is another player's turn.");
			let progress = false;
			let actionResult: Record<string, unknown> = { kind: input.kind, accepted: true };
			if (input.kind === "open_character") {
				const activeSongs = match.songs.filter((item: any) => item.status === "active");
				let newlyRevealedCount = 0;
				const completedSongIds: string[] = [];
				for (const song of activeSongs) {
					const revealed = jsonArray<number>(song.revealedIndices);
					const reveal = revealCharacter(song.title, revealed, input.character);
					newlyRevealedCount += reveal.newlyRevealedCount;
					progress ||= reveal.newlyRevealedCount > 0;
					if (reveal.autoCompleted) completedSongIds.push(song.id);
					await tx.letterGameMatchSong.update({
						where: { id: song.id },
						data: {
							revealedIndices: reveal.revealedIndices,
							...(reveal.autoCompleted
								? {
										status: "completed",
										completionReason: "all_characters_revealed",
										completedByUserId: userId,
										completedAt: new Date(),
									  }
								: {}),
						},
					});
				}
				if (actor.scoringEligible && newlyRevealedCount > 0)
					await tx.letterGameMatchPlayer.update({ where: { id: actor.id }, data: { score: { increment: newlyRevealedCount } } });
					actionResult = {
						...actionResult,
						newlyRevealedCount,
						points: actor.scoringEligible ? newlyRevealedCount : 0,
						completedSongCount: completedSongIds.length,
					completedSongIds,
				};
			} else if (input.kind === "guess_song") {
				const song = match.songs.find((item: any) => item.slotId === input.slotId && item.status === "active");
				if (!song) throw new AppError(404, "song_slot_not_found", "Song slot not found or already completed.");
				const aliases = jsonArray<string>(song.aliases);
				if (!guessSongMatches(input.guess, song.title, aliases)) {
					actionResult = { ...actionResult, correct: false };
				} else {
					const revealed = jsonArray<number>(song.revealedIndices);
					const blind = revealed.length === 0;
					const tokens = buildLetterTokens(song.title, revealed);
					const points = guessScore(remainingCharacterCount(tokens), blind);
					await tx.letterGameMatchSong.update({
						where: { id: song.id },
						data: { status: "completed", completionReason: "guessed", completedByUserId: userId, completedAt: new Date() },
					});
					if (actor.scoringEligible)
						await tx.letterGameMatchPlayer.update({ where: { id: actor.id }, data: { score: { increment: points } } });
					progress = true;
					actionResult = { ...actionResult, correct: true, blind, points, completionReason: "guessed" };
				}
			} else {
				const song = match.songs.find((item: any) => item.slotId === input.slotId && item.status === "active");
				if (!song) throw new AppError(404, "song_slot_not_found", "Song slot not found or already completed.");
				const cost = input.visibility === "public" ? room.publicHintCost : room.privateHintCost;
				if (!actor.scoringEligible || actor.score < cost)
					throw new AppError(400, "insufficient_score", "Not enough score for this hint.");
				const previous = await tx.letterGamePlayerFact.findFirst({
					where: {
						matchId,
						songId: song.id,
						factType: input.hintType,
						visibility: input.visibility,
						...(input.visibility === "private" ? { userId } : {}),
					},
				});
				if (previous) {
					actionResult = {
						...actionResult,
						hint: {
							type: previous.factType,
							visibility: previous.visibility,
							value: previous.value,
							cost: 0,
							alreadyPurchased: true,
						},
					};
				} else {
					const value = await this.resolveHintValue(input, song.songIdentifier, userId, matchId, song.id, tx);
					await tx.letterGameMatchPlayer.update({ where: { id: actor.id }, data: { score: { decrement: cost } } });
					const fact = await tx.letterGamePlayerFact.create({
						data: { matchId, songId: song.id, userId, factType: input.hintType, visibility: input.visibility, value, cost },
					});
					actionResult = {
						...actionResult,
						hint: { type: fact.factType, visibility: fact.visibility, value: fact.value, cost },
					};
				}
			}

			const activeSongs = await tx.letterGameMatchSong.count({ where: { matchId, status: "active" } });
			const matchFinished = activeSongs === 0;
			const nextOrder = this.nextTurn(order, match.currentTurnIndex, players);
			const wrapped = nextOrder.userId === userId || (nextOrder.index !== match.currentTurnIndex && nextOrder.index <= match.currentTurnIndex);
			const noProgressRounds = progress ? 0 : wrapped ? match.noProgressRounds + 1 : match.noProgressRounds;
			const stalled = !matchFinished && noProgressRounds >= room.stalledRoundLimit;
			const finished = matchFinished || stalled || !nextOrder.userId;
			const nextRevision = match.revision + 1;
			const updated = await tx.letterGameMatch.update({
				where: { id: matchId },
				data: {
					revision: nextRevision,
					noProgressRounds,
					currentTurnIndex: nextOrder.index,
					turnDeadline: finished ? null : new Date(Date.now() + room.turnDurationSeconds * 1000),
					...(finished ? { status: stalled && !matchFinished ? "abandoned" : "finished", endedAt: new Date() } : {}),
				},
			});
			actionResult = {
				...actionResult,
				revision: updated.revision,
				matchFinished: finished,
				matchStatus: updated.status,
				nextTurnUserId: finished ? null : nextOrder.userId,
			};
			await tx.letterGameAction.create({
				data: {
					matchId,
					actorId: userId,
					idempotencyKey,
					sequence: nextRevision,
					actionType: input.kind,
					payload: input,
					result: actionResult,
				},
			});
			return actionResult;
		});
		return result;
	}

	async leaveMatch(userId: string, matchId: string) {
		return this.prisma.$transaction(async (tx: any) => {
			const matchLocation = await tx.letterGameMatch.findUnique({ where: { id: matchId }, select: { roomId: true } });
			if (!matchLocation) throw new AppError(404, "match_not_found", "Letter game match not found.");
			await tx.$executeRawUnsafe("SELECT pg_advisory_xact_lock(hashtextextended($1, 0))", matchLocation.roomId);
			const match = await tx.letterGameMatch.findUnique({ where: { id: matchId }, include: { players: true } });
			if (!match) throw new AppError(404, "match_not_found", "Letter game match not found.");
			const player = match.players.find((item: any) => item.userId === userId);
			if (!player) throw new AppError(403, "match_access_denied", "You are not a player in this match.");
			if (match.status !== "active") return { left: true, revision: match.revision, matchFinished: true };
			if (player.status !== "active") return { left: true, revision: match.revision, matchFinished: match.status !== "active" };

			await tx.letterGameMatchPlayer.update({ where: { id: player.id }, data: { status: "left" } });
			const players = match.players.map((item: any) => (item.id === player.id ? { ...item, status: "left" } : item));
			const order = jsonArray<string>(match.turnOrder);
			const isCurrentTurn = order[match.currentTurnIndex] === userId;
			const next = isCurrentTurn
				? this.nextTurn(order, match.currentTurnIndex, players)
				: { index: match.currentTurnIndex, userId: order[match.currentTurnIndex] ?? null };
			const hasActivePlayers = players.some((item: any) => item.status === "active");
			const finished = !hasActivePlayers || !next.userId;
			const room = await tx.letterGameRoom.findUnique({ where: { id: match.roomId }, select: { turnDurationSeconds: true } });
			const updated = await tx.letterGameMatch.update({
				where: { id: matchId },
				data: {
					revision: { increment: 1 },
					currentTurnIndex: next.index,
					turnDeadline: finished ? null : new Date(Date.now() + (room?.turnDurationSeconds ?? DEFAULT_TURN_SECONDS) * 1000),
					...(finished ? { status: "abandoned", endedAt: new Date() } : {}),
				},
			});
			return { left: true, revision: updated.revision, matchFinished: finished, nextTurnUserId: finished ? null : next.userId };
		});
	}

	async expireDueMatches() {
		const expiredMatchIds: string[] = [];
		const dueMatches = await this.prisma.letterGameMatch.findMany({
			where: { status: "active", turnDeadline: { lte: new Date() } },
			select: { id: true },
			take: 100,
		});
		for (const due of dueMatches) {
			await this.prisma.$transaction(async (tx: any) => {
				const matchLocation = await tx.letterGameMatch.findUnique({ where: { id: due.id }, select: { roomId: true } });
				if (!matchLocation) return;
				await tx.$executeRawUnsafe("SELECT pg_advisory_xact_lock(hashtextextended($1, 0))", matchLocation.roomId);
				const match = await tx.letterGameMatch.findUnique({ where: { id: due.id }, include: { players: true } });
				if (!match || match.status !== "active" || !match.turnDeadline || match.turnDeadline > new Date()) return;
				const order = jsonArray<string>(match.turnOrder);
				const next = this.nextTurn(order, match.currentTurnIndex, match.players);
				const noProgressRounds = match.noProgressRounds + (next.index <= match.currentTurnIndex ? 1 : 0);
				const room = await tx.letterGameRoom.findUnique({
					where: { id: match.roomId },
					select: { stalledRoundLimit: true, turnDurationSeconds: true },
				});
				const stalled = noProgressRounds >= (room?.stalledRoundLimit ?? DEFAULT_STALLED_ROUNDS);
				await tx.letterGameMatch.update({
					where: { id: match.id },
					data: {
						currentTurnIndex: next.index,
						noProgressRounds,
						revision: { increment: 1 },
						turnDeadline:
							stalled || !next.userId
								? null
								: new Date(Date.now() + (room?.turnDurationSeconds ?? DEFAULT_TURN_SECONDS) * 1000),
						...(stalled || !next.userId ? { status: "abandoned", endedAt: new Date() } : {}),
					},
				});
				expiredMatchIds.push(match.id);
			});
		}
		return expiredMatchIds;
	}

	async dissolveEmptyRooms(limit = 100, onRoomChanged?: (roomId: string) => Promise<void>) {
		const now = new Date();
		const staleBefore = new Date(now.getTime() - MEMBER_STALE_AFTER_MS);
		const dissolvedRoomIds = new Set<string>();
		const staleMembers =
			typeof this.prisma.letterGameRoomMember?.findMany === "function"
				? await this.prisma.letterGameRoomMember.findMany({
						where: {
							status: "accepted",
							room: { status: "open" },
							OR: [{ lastSeenAt: null }, { lastSeenAt: { lt: staleBefore } }],
						},
						select: { roomId: true, userId: true },
						take: limit,
					})
				: [];
		for (const member of staleMembers) {
			const result = await this.leaveRoomMember(member.userId, member.roomId, { staleBefore }).catch(() => null);
			if (result?.dissolved) dissolvedRoomIds.add(member.roomId);
			else if (result?.left) await onRoomChanged?.(member.roomId);
		}

		const candidates = await this.prisma.letterGameRoom.findMany({
			where: { status: "open", members: { none: { status: "accepted" } } },
			select: { id: true },
			take: limit,
		});
		for (const candidate of candidates) {
			await this.prisma.$transaction(async (tx: any) => {
				await tx.$executeRawUnsafe("SELECT pg_advisory_xact_lock(hashtextextended($1, 0))", candidate.id);
				const room = await tx.letterGameRoom.findUnique({ where: { id: candidate.id }, select: { status: true } });
				if (room?.status !== "open") return;
				if (await this.closeRoomIfEmpty(tx, candidate.id, now)) dissolvedRoomIds.add(candidate.id);
			});
		}
		return [...dissolvedRoomIds];
	}

	async history(userId: string, roomId: string) {
		const room = await this.prisma.letterGameRoom.findUnique({ where: { id: roomId }, select: { id: true } });
		if (!room) throw new AppError(404, "room_not_found", "Letter game room not found.");
		const member = await this.prisma.letterGameRoomMember.findUnique({ where: { roomId_userId: { roomId, userId } } });
		if (!member || member.status === "kicked")
			throw new AppError(403, "room_access_denied", "You are not a member of this room.");
		return this.prisma.letterGameMatch.findMany({
			where: { roomId, status: { in: ["finished", "abandoned"] } },
			orderBy: { sequence: "desc" },
			take: 50,
			include: {
				players: { orderBy: { score: "desc" }, select: { userId: true, score: true, scoringEligible: true, turnOrder: true } },
			},
		});
	}

	private async closeRoomIfEmpty(tx: any, roomId: string, now: Date) {
		const acceptedMemberCount = await tx.letterGameRoomMember.count({ where: { roomId, status: "accepted" } });
		if (acceptedMemberCount > 0) return false;

		const activeMatches = await tx.letterGameMatch.findMany({
			where: { roomId, status: "active" },
			select: { id: true },
		});
		const activeMatchIds = activeMatches.map((match: any) => match.id);
		if (activeMatchIds.length > 0) {
			await tx.letterGameMatchPlayer.updateMany({
				where: { matchId: { in: activeMatchIds }, status: "active" },
				data: { status: "left" },
			});
			await tx.letterGameMatch.updateMany({
				where: { id: { in: activeMatchIds }, status: "active" },
				data: { status: "abandoned", endedAt: now, turnDeadline: null, revision: { increment: 1 } },
			});
		}
		await tx.letterGameRoomMember.updateMany({
			where: { roomId, status: { in: ["accepted", "pending"] } },
			data: { status: "left", leftAt: now },
		});
		const closed = await tx.letterGameRoom.updateMany({
			where: { id: roomId, status: "open" },
			data: { status: "closed", closedAt: now },
		});
		return closed.count > 0;
	}

	private validateSettings(input: LetterGameRoomSettingsInput) {
		const publicHintCost =
			input.visibility === "public"
				? DEFAULT_PUBLIC_HINT_COST
				: intInRange(input.publicHintCost, DEFAULT_PUBLIC_HINT_COST, 1, 100);
		const privateHintCost =
			input.visibility === "public"
				? DEFAULT_PRIVATE_HINT_COST
				: intInRange(input.privateHintCost, DEFAULT_PRIVATE_HINT_COST, 1, 100);
		if (privateHintCost <= publicHintCost)
			throw new AppError(400, "invalid_hint_costs", "Private hint cost must be greater than public hint cost.");
		const songCount =
			input.songCount === null || input.songCount === undefined ? null : intInRange(input.songCount, 1, 1, 5000);
		return {
			visibility: input.visibility,
			hostMode: input.hostMode ?? "fixed",
			turnDurationSeconds: intInRange(input.turnDurationSeconds, DEFAULT_TURN_SECONDS, 15, 120),
			stalledRoundLimit: intInRange(input.stalledRoundLimit, DEFAULT_STALLED_ROUNDS, 1, 10),
			songCountOverride: songCount,
			publicHintCost,
			privateHintCost,
			selectionMode: input.selectionMode ?? "filtered_random",
			selectionConfig: this.normalizeSelectionConfig(input.selectionMode ?? "filtered_random", input.selectionConfig),
		};
	}

	private normalizeSelectionConfig(
		mode: "filtered_random" | "collection",
		value: Record<string, unknown> | undefined,
	): Record<string, unknown> {
		const config = jsonObject(value);
		if (mode === "collection") {
			return { collectionIds: normalizeSourceIds(config.collectionIds) };
		}
		const chartTypes = normalizeSourceIds(config.chartTypes).map((type) => type.toLowerCase());
		if (chartTypes.some((type) => type !== "standard" && type !== "dx")) {
			throw new AppError(400, "invalid_chart_types", "Chart types must contain only standard or dx.");
		}
		const minVersion = typeof config.minVersion === "string" ? config.minVersion.trim() || null : null;
		const maxVersion = typeof config.maxVersion === "string" ? config.maxVersion.trim() || null : null;
		return {
			excludeDeleted: typeof config.excludeDeleted === "boolean" ? config.excludeDeleted : true,
			englishOnly: typeof config.englishOnly === "boolean" ? config.englishOnly : false,
			minVersion,
			maxVersion,
			categories: normalizeSourceIds(config.categories),
			chartTypes: [...new Set(chartTypes)],
		};
	}

	private async requireHost(userId: string, roomId: string) {
		const room = await this.prisma.letterGameRoom.findUnique({ where: { id: roomId } });
		if (!room) throw new AppError(404, "room_not_found", "Letter game room not found.");
		if (room.status !== "open") throw new AppError(410, "room_closed", "This letter game room has been dissolved.");
		if (room.hostUserId !== userId) throw new AppError(403, "host_required", "Only the room host can perform this action.");
		return room;
	}

	private async selectSongs(userId: string, room: any, database: PrismaLike = this.prisma) {
		const config = this.normalizeSelectionConfig(room.selectionMode, jsonObject(room.selectionConfig));
		let ids: string[] = [];
		if (room.selectionMode === "collection") {
			ids = await this.collectionSongIds(userId, config, database);
		} else {
			const where: any = {};
			if (config.excludeDeleted !== false) where.disabled = false;
			const categories = normalizeSourceIds(config.categories);
			if (categories.length > 0) where.category = { in: categories };
			const chartTypes = normalizeSourceIds(config.chartTypes);
			if (chartTypes.length > 0) {
				const databaseTypes = chartTypes.flatMap((type) => (type === "standard" ? ["standard", "std", "sd"] : ["dx"]));
				where.sheets = { some: { chartType: { in: databaseTypes }, disabled: false } };
			}
			const minVersion = typeof config.minVersion === "string" ? config.minVersion : null;
			const maxVersion = typeof config.maxVersion === "string" ? config.maxVersion : null;
			if (minVersion !== null || maxVersion !== null) {
				const versionNames = (await this.catalogService.listVersions()).map((version: any) => String(version.version));
				const firstIndex = minVersion === null ? 0 : versionNames.indexOf(minVersion);
				const lastIndex = maxVersion === null ? versionNames.length - 1 : versionNames.indexOf(maxVersion);
				if (firstIndex < 0 || lastIndex < 0 || firstIndex > lastIndex) {
					throw new AppError(400, "invalid_version_range", "The selected version range is invalid.");
				}
				where.version = { in: versionNames.slice(firstIndex, lastIndex + 1) };
			}
			const rows = await database.song.findMany({ where, select: { songIdentifier: true, title: true } });
			ids = shuffle(
				rows
					.filter((row: any) => !config.englishOnly || isEnglishOnlyTitle(row.title))
					.map((row: any) => row.songIdentifier),
			);
		}
		if (ids.length === 0) return [];
		if (typeof database.sheet?.findMany === "function") {
			const playableSheets = await database.sheet.findMany({
				where: { songIdentifier: { in: ids }, disabled: false },
				select: { songIdentifier: true, chartType: true },
			});
			const playableIds = new Set(
				playableSheets
					.filter((sheet: any) => !isUtageChartType(sheet.chartType))
					.map((sheet: any) => String(sheet.songIdentifier)),
			);
			ids = ids.filter((id) => playableIds.has(id));
		}
		if (ids.length === 0) return [];
		const songs = await database.song.findMany({
			where: {
				songIdentifier: { in: ids },
				...(room.selectionMode === "filtered_random" && config.excludeDeleted !== false ? { disabled: false } : {}),
			},
			select: { songIdentifier: true, title: true },
		});
		const aliases = (await this.catalogService.listAliases(undefined, undefined)).filter(
			(alias) => alias.status === "approved" || alias.status === "imported",
		);
		const aliasesBySong = new Map<string, string[]>();
		for (const alias of aliases)
			aliasesBySong.set(alias.songIdentifier, [...(aliasesBySong.get(alias.songIdentifier) ?? []), alias.aliasText]);
		return shuffle(songs)
			.filter((song: any) => !config.englishOnly || isEnglishOnlyTitle(song.title))
			.map((song: any) => ({ ...song, aliases: aliasesBySong.get(song.songIdentifier) ?? [] }));
	}

	private async collectionSongIds(
		userId: string,
		selectionConfig: Record<string, unknown>,
		database: PrismaLike = this.prisma,
	): Promise<string[]> {
		const collectionIds = normalizeSourceIds(selectionConfig.collectionIds);
		if (collectionIds.length === 0) return [];
		const collections = await database.songCollection.findMany({
			where: { id: { in: collectionIds }, userId, deletedAt: null },
			include: { items: { where: { deletedAt: null }, select: { songId: true } } },
		});
		if (collections.length !== collectionIds.length) {
			throw new AppError(404, "collection_not_found", "One or more selected collections were not found.");
		}
		return normalizeSourceIds(collections.flatMap((collection: any) => collection.items.map((item: any) => item.songId)));
	}

	private nextTurn(order: string[], currentIndex: number, players: any[]) {
		if (order.length === 0) return { index: currentIndex, userId: null as string | null };
		for (let step = 1; step <= order.length; step += 1) {
			const index = (currentIndex + step) % order.length;
			const userId = order[index];
			if (players.some((player) => player.userId === userId && player.status === "active")) return { index, userId };
		}
		return { index: currentIndex, userId: null as string | null };
	}

	private async resolveHintValue(
		input: Extract<LetterGameActionInput, { kind: "buy_hint" }>,
		songIdentifier: string,
		userId: string,
		matchId: string,
		songId: string,
		tx: any,
	) {
		if (input.hintType === "white_chart") {
			const sheets = await tx.sheet.findMany({ where: { songIdentifier, disabled: false }, select: { difficulty: true } });
			return sheets.some((sheet: any) => /re\s*:??\s*master|remaster/iu.test(sheet.difficulty));
		}
		if (input.hintType === "constant") {
			if (!input.difficulty?.trim())
				throw new AppError(400, "difficulty_required", "Difficulty is required for a constant hint.");
			const whiteKnowledge = await tx.letterGamePlayerFact.findFirst({
				where: { matchId, songId, factType: "white_chart", OR: [{ userId, visibility: "private" }, { visibility: "public" }] },
			});
			if (!whiteKnowledge || whiteKnowledge.value !== true)
				throw new AppError(400, "white_chart_unknown", "Reveal white chart availability before requesting a constant.");
			const sheet = await tx.sheet.findFirst({
				where: { songIdentifier, difficulty: input.difficulty.trim(), disabled: false },
				select: { internalLevelValue: true, levelValue: true },
			});
			if (!sheet) throw new AppError(404, "chart_not_found", "Chart difficulty was not found.");
			const rawValue = sheet.internalLevelValue ?? sheet.levelValue ?? null;
			return { difficulty: input.difficulty.trim(), value: rawValue === null ? null : Number(rawValue) };
		}
		const [song, sheets, versions] = await Promise.all([
			tx.song.findUnique({ where: { songIdentifier }, select: { version: true } }),
			tx.sheet.findMany({ where: { songIdentifier, disabled: false }, select: { version: true } }),
			this.catalogService.listVersions(),
		]);
		const candidates = [
			...new Set(
				[song?.version, ...sheets.map((sheet: any) => sheet.version)].filter(
					(value): value is string => typeof value === "string" && value.trim().length > 0,
				),
			),
		];
		const rank = new Map<string, { timestamp: number; index: number }>();
		versions.forEach((version: any, index: number) =>
			rank.set(version.version, {
				timestamp: version.releaseDate ? Date.parse(version.releaseDate) : Number.POSITIVE_INFINITY,
				index,
			}),
		);
		candidates.sort((left, right) => {
			const leftRank = rank.get(left) ?? { timestamp: Number.POSITIVE_INFINITY, index: Number.MAX_SAFE_INTEGER };
			const rightRank = rank.get(right) ?? { timestamp: Number.POSITIVE_INFINITY, index: Number.MAX_SAFE_INTEGER };
			return leftRank.timestamp - rightRank.timestamp || leftRank.index - rightRank.index || left.localeCompare(right);
		});
		return candidates[0] ?? null;
	}

	private async serializeRoom(room: any, members: any[]) {
		const userIds = [...new Set<string>(members.map((member: any) => String(member.userId)))];
		const config = this.normalizeSelectionConfig(room.selectionMode, jsonObject(room.selectionConfig));
		const collectionIds = room.selectionMode === "collection" ? normalizeSourceIds(config.collectionIds) : [];
		const [profiles, users, selectedCollections] = await Promise.all([
			this.prisma.profile.findMany({
				where: { userId: { in: userIds }, isActive: true },
				select: { userId: true, avatarUrl: true },
			}),
			this.prisma.user.findMany({
				where: { id: { in: userIds } },
				select: { id: true, username: true },
			}),
			collectionIds.length === 0
				? Promise.resolve([])
				: this.prisma.songCollection.findMany({
						where: { id: { in: collectionIds }, userId: room.hostUserId, deletedAt: null },
						select: { id: true, name: true, items: { where: { deletedAt: null }, select: { songId: true } } },
					}),
		]);
		const profileByUser = new Map(profiles.map((profile: any) => [profile.userId, profile]));
		const handleByUser = new Map(
			users.map((user: any) => [user.id, user.username]),
		);
		const collectionById = new Map(selectedCollections.map((collection: any) => [collection.id, collection]));
		return {
			id: room.id,
			code: room.code,
			visibility: room.visibility,
			hostMode: room.hostMode,
			hostUserId: room.hostUserId,
			status: room.status,
			settings: {
				turnDurationSeconds: room.turnDurationSeconds,
				stalledRoundLimit: room.stalledRoundLimit,
				songCountOverride: room.songCountOverride,
				publicHintCost: room.publicHintCost,
				privateHintCost: room.privateHintCost,
				selectionMode: room.selectionMode,
				selectionConfig: config,
				selectedCollections: collectionIds.flatMap((id) => {
					const collection = collectionById.get(id) as any;
					return collection
						? [{ id, name: collection.name, songCount: new Set(collection.items.map((item: any) => item.songId)).size }]
						: [];
				}),
			},
			memberCount: members.filter((member) => member.status === "accepted").length,
			members: members.map((member) => ({
				id: member.id ?? null,
				userId: member.userId,
				status: member.status,
				seatOrder: member.seatOrder,
				displayName: handleByUser.get(member.userId) ?? null,
				avatarUrl: profileByUser.get(member.userId)?.avatarUrl ?? null,
			})),
			latestMatch: room.matches?.[0] ?? null,
		};
	}
}
