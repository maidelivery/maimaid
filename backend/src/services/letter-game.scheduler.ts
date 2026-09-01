import { inject, injectable } from "tsyringe";
import { LetterGameService } from "./letter-game.service.js";
import { LetterGameConnectionHub } from "./letter-game.connection.js";

@injectable()
export class LetterGameTurnScheduler {
	private timer: ReturnType<typeof setInterval> | null = null;
	private running = false;

	constructor(
		@inject(LetterGameService) private readonly service: LetterGameService,
		@inject(LetterGameConnectionHub) private readonly hub: LetterGameConnectionHub,
	) {}

	start() {
		if (this.timer) return;
		void this.tick();
		this.timer = setInterval(() => {
			void this.tick();
		}, 1000);
		this.timer.unref?.();
	}

	stop() {
		if (!this.timer) return;
		clearInterval(this.timer);
		this.timer = null;
	}

	private async tick() {
		if (this.running) return;
		this.running = true;
		try {
			const matchIds = await this.service.expireDueMatches();
			await Promise.all(matchIds.map((matchId) => this.hub.broadcastMatch(matchId)));
			const roomIds = await this.service.dissolveEmptyRooms();
			for (const roomId of roomIds) this.hub.closeRoom(roomId);
		} catch (error) {
			console.error("[letter-game] scheduler tick failed", error);
		} finally {
			this.running = false;
		}
	}
}
