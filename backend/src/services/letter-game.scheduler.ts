import { inject, injectable } from "tsyringe";
import { LetterGameService } from "./letter-game.service.js";

@injectable()
export class LetterGameTurnScheduler {
	private timer: ReturnType<typeof setInterval> | null = null;

	constructor(@inject(LetterGameService) private readonly service: LetterGameService) {}

	start() {
		if (this.timer) return;
		this.timer = setInterval(() => {
			void this.service.expireDueMatches().catch((error) => console.error("[letter-game] turn expiry failed", error));
		}, 1000);
		this.timer.unref?.();
	}

	stop() {
		if (!this.timer) return;
		clearInterval(this.timer);
		this.timer = null;
	}
}
