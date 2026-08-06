import { cn } from "@/lib/utils";

/**
 * Maps a normalized difficulty to its maimai signature colour token.
 * Unknown values fall back to muted styling rather than an arbitrary colour.
 */
const DIFFICULTY_CLASS: Record<string, string> = {
	basic: "bg-diff-basic/12 text-diff-basic",
	advanced: "bg-diff-advanced/15 text-diff-advanced",
	expert: "bg-diff-expert/12 text-diff-expert",
	master: "bg-diff-master/12 text-diff-master",
	remaster: "bg-diff-remaster/18 text-diff-remaster",
};

type DifficultyBadgeProps = {
	/** Raw difficulty value, e.g. "master" or "Re:MASTER". */
	difficulty?: string | null;
	/** Pre-formatted difficulty label to display. */
	label: string;
	/** Pre-formatted chart-type label ("STD" / "DX" / "UTAGE"), shown alongside. */
	chartType?: string;
	className?: string;
};

export function DifficultyBadge({ difficulty, label, chartType, className }: DifficultyBadgeProps) {
	const key = (difficulty ?? "").trim().toLowerCase().replace(/[^a-z]/gu, "");
	const tone = DIFFICULTY_CLASS[key] ?? "bg-muted text-muted-foreground";

	return (
		<span className={cn("inline-flex items-center gap-1.5 text-xs", className)}>
			{chartType ? (
				<span className="rounded-md bg-muted px-1.5 py-0.5 font-semibold text-muted-foreground">{chartType}</span>
			) : null}
			<span className={cn("rounded-md px-1.5 py-0.5 font-semibold", tone)}>{label}</span>
		</span>
	);
}
