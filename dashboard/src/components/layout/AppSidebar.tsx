import type { Dispatch, SetStateAction } from "react";
import type { LucideIcon } from "lucide-react";
import { LogOutIcon } from "lucide-react";
import { Avatar as UiAvatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
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
		sessionEmail,
		roleLabel,
		RoleIcon,
		activeProfileAvatarUrl,
		onLogout,
	} = props;

	const { t } = useTranslation();

	return (
		<div className="flex h-full flex-col">
			<div className="border-b border-border/60 px-4 py-2.5">
				<div className="flex items-center gap-2">
					<span className="size-2 rounded-full bg-primary" />
					<p className="text-sm font-medium">maimaid Dashboard</p>
				</div>
				<p className="text-xs leading-4 text-muted-foreground">{enabledProfileName}</p>
			</div>

			<div className="flex-1 overflow-y-auto px-3 py-4">
				<div className="flex flex-col gap-1">
					<p className="px-2 pb-1 text-xs text-muted-foreground">{t("sidebar:workspace")}</p>
					{workspaceTabs.map((item) => {
						const ItemIcon = item.icon;
						const active = tab === item.value;
						return (
							<Button
								key={item.value}
								variant="ghost"
								className={cn("h-9 justify-start rounded-md px-2", active && "bg-accent text-accent-foreground")}
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
					<p className="px-2 pb-1 text-xs text-muted-foreground">{t("sidebar:management")}</p>
					{managementTabs.map((item) => {
						const ItemIcon = item.icon;
						const active = tab === item.value;
						return (
							<Button
								key={item.value}
								variant="ghost"
								className={cn("h-9 justify-start rounded-md px-2", active && "bg-accent text-accent-foreground")}
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

			<div className="border-t border-border/60 px-3 py-3">
				<div className="mb-3 flex items-center gap-2 rounded-md border border-border/60 bg-muted/20 px-2 py-2">
					<UiAvatar className="size-8 rounded-md">
						<AvatarImage src={activeProfileAvatarUrl ?? undefined} alt={sessionEmail} />
						<AvatarFallback>{sessionEmail.slice(0, 1).toUpperCase()}</AvatarFallback>
					</UiAvatar>
					<div className="min-w-0 flex-1">
						<p className="truncate text-xs">{sessionEmail}</p>
						<p className="text-[11px] text-muted-foreground">{roleLabel}</p>
					</div>
					<RoleIcon className="size-4 text-muted-foreground" />
				</div>
				<Button variant="outline" className="w-full justify-start" onClick={onLogout}>
					<LogOutIcon data-icon="inline-start" />
					{t("sidebar:logout")}
				</Button>
			</div>
		</div>
	);
}
