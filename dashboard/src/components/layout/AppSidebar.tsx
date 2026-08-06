import type { Dispatch, SetStateAction } from "react";
import Image from "next/image";
import type { LucideIcon } from "lucide-react";
import { LogOutIcon } from "lucide-react";
import { Avatar as UiAvatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { HandleText } from "@/components/ui/handle-text";
import type { NavigationTabItem } from "@/lib/app-types";
import { cn } from "@/lib/utils";
import { useTranslation } from "react-i18next";

type AppSidebarProps = {
	workspaceTabs: NavigationTabItem[];
	managementTabs: NavigationTabItem[];
	tab: string;
	setTab: Dispatch<SetStateAction<string>>;
	setMobileDrawerOpen: Dispatch<SetStateAction<boolean>>;
	enabledProfileName: string;
	sessionHandle: string;
	sessionEmail: string;
	roleLabel: string;
	RoleIcon: LucideIcon;
	activeProfileAvatarUrl: string | null;
	onLogout: () => void;
};

export function AppSidebar(props: AppSidebarProps) {
	const {
		workspaceTabs,
		managementTabs,
		tab,
		setTab,
		setMobileDrawerOpen,
		enabledProfileName,
		sessionHandle,
		sessionEmail,
		roleLabel,
		RoleIcon,
		activeProfileAvatarUrl,
		onLogout,
	} = props;

	const { t } = useTranslation();

	return (
		<div className="flex h-full flex-col">
			<div className="flex h-14 shrink-0 items-center border-b border-border/60 px-4">
				<div className="flex min-w-0 items-center gap-2.5">
					<Image
						src="/app-icon.png"
						alt=""
						width={32}
						height={32}
						unoptimized
						priority
						className="size-8 shrink-0 drop-shadow-sm dark:hidden"
					/>
					<Image
						src="/app-icon-dark.png"
						alt=""
						width={32}
						height={32}
						unoptimized
						priority
						className="hidden size-8 shrink-0 dark:block"
					/>
					<div className="min-w-0">
						<p className="truncate text-sm font-bold tracking-tight">maimaid</p>
						<p className="truncate text-xs leading-4 text-muted-foreground">{enabledProfileName}</p>
					</div>
				</div>
			</div>

			<div className="flex-1 overflow-y-auto px-3 py-4">
				<div className="flex flex-col gap-1">
					<p className="px-2.5 pb-1.5 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground/70">
						{t("sidebar:workspace")}
					</p>
					{workspaceTabs.map((item) => {
						const ItemIcon = item.icon;
						const active = tab === item.value;
						return (
							<Button
								key={item.value}
								variant="ghost"
								className={cn(
									"h-9 justify-start rounded-lg px-2.5 font-medium transition-colors",
									active
										? "bg-primary/10 text-primary hover:bg-primary/15 hover:text-primary"
										: "text-muted-foreground hover:text-foreground",
								)}
								onClick={() => {
									setTab(item.value);
									setMobileDrawerOpen(false);
								}}
							>
								<ItemIcon data-icon="inline-start" />
								{item.label}
							</Button>
						);
					})}
				</div>

				<div className="mt-5 flex flex-col gap-1">
					<p className="px-2.5 pb-1.5 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground/70">
						{t("sidebar:management")}
					</p>
					{managementTabs.map((item) => {
						const ItemIcon = item.icon;
						const active = tab === item.value;
						return (
							<Button
								key={item.value}
								variant="ghost"
								className={cn(
									"h-9 justify-start rounded-lg px-2.5 font-medium transition-colors",
									active
										? "bg-primary/10 text-primary hover:bg-primary/15 hover:text-primary"
										: "text-muted-foreground hover:text-foreground",
								)}
								onClick={() => {
									setTab(item.value);
									setMobileDrawerOpen(false);
								}}
							>
								<ItemIcon data-icon="inline-start" />
								{item.label}
							</Button>
						);
					})}
				</div>
			</div>

			<div className="border-t border-border/50 px-3 py-3">
				<div className="mb-2 flex items-center gap-2.5 rounded-xl px-1.5 py-1.5">
					<UiAvatar className="size-9 rounded-xl ring-1 ring-primary/15">
						<AvatarImage src={activeProfileAvatarUrl ?? undefined} alt={sessionHandle} />
						<AvatarFallback className="bg-primary/10 text-xs font-semibold text-primary">
							{sessionHandle.slice(0, 1).toUpperCase()}
						</AvatarFallback>
					</UiAvatar>
					<div className="min-w-0 flex-1">
						<HandleText handle={sessionHandle} className="block truncate text-xs font-semibold" />
						<p className="truncate text-[11px] leading-4 text-muted-foreground">{sessionEmail}</p>
					</div>
					<RoleIcon className="size-3.5 shrink-0 text-primary/60" role="img" aria-label={roleLabel} />
				</div>
				<Button
					variant="ghost"
					className="h-9 w-full justify-start rounded-lg px-2.5 text-muted-foreground hover:text-foreground"
					onClick={onLogout}
				>
					<LogOutIcon data-icon="inline-start" />
					{t("sidebar:logout")}
				</Button>
			</div>
		</div>
	);
}
