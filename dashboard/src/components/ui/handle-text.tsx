import { cn } from "@/lib/utils";

type HandleTextProps = {
	handle: string;
	className?: string;
	discriminatorClassName?: string;
};

function splitHandle(handle: string) {
	const hashIndex = handle.lastIndexOf("#");
	if (hashIndex <= 0 || handle.length - hashIndex !== 5) {
		return null;
	}

	const username = handle.slice(0, hashIndex);
	const discriminator = handle.slice(hashIndex);
	if (!username || /\s/gu.test(discriminator)) {
		return null;
	}

	return { username, discriminator };
}

export function HandleText({ handle, className, discriminatorClassName }: HandleTextProps) {
	const parts = splitHandle(handle);
	if (!parts) {
		return <span className={className}>{handle}</span>;
	}

	return (
		<span className={className}>
			<span>{parts.username}</span>
			<span className={cn("text-muted-foreground", discriminatorClassName)}>{parts.discriminator}</span>
		</span>
	);
}
