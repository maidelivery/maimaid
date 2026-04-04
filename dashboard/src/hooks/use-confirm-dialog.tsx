import { useCallback, useRef, useState } from "react";
import type { ReactNode } from "react";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { useTranslation } from "react-i18next";

export type ConfirmDialogTone = "default" | "destructive";

export type ConfirmDialogOptions = {
	title?: string;
	description?: string;
	confirmText?: string;
	cancelText?: string;
	tone?: ConfirmDialogTone;
};

type ConfirmDialogState = {
	open: boolean;
	options: ConfirmDialogOptions;
};

export function useConfirmDialog() {
	const { t } = useTranslation("app");
	const resolverRef = useRef<((confirmed: boolean) => void) | null>(null);
	const [state, setState] = useState<ConfirmDialogState>({
		open: false,
		options: {
			title: "",
			description: "",
			confirmText: "",
			cancelText: "",
			tone: "default",
		},
	});

	const settle = useCallback((confirmed: boolean) => {
		const resolver = resolverRef.current;
		resolverRef.current = null;
		if (resolver) {
			resolver(confirmed);
		}
	}, []);

	const confirm = useCallback(
		(options: ConfirmDialogOptions): Promise<boolean> => {
			settle(false);
			setState({
				open: true,
				options: {
					title: t("dialogTitle"),
					description: "",
					confirmText: t("dialogConfirm"),
					cancelText: t("dialogCancel"),
					tone: "default",
					...options,
				},
			});
			return new Promise<boolean>((resolve) => {
				resolverRef.current = resolve;
			});
		},
		[settle, t],
	);

	const handleOpenChange = useCallback(
		(open: boolean) => {
			setState((previous) => ({ ...previous, open }));
			if (!open) {
				settle(false);
			}
		},
		[settle],
	);

	const handleCancel = useCallback(() => {
		setState((previous) => ({ ...previous, open: false }));
		settle(false);
	}, [settle]);

	const handleConfirm = useCallback(() => {
		setState((previous) => ({ ...previous, open: false }));
		settle(true);
	}, [settle]);

	const dialogNode: ReactNode = (
		<AlertDialog open={state.open} onOpenChange={handleOpenChange}>
			<AlertDialogContent>
				<AlertDialogHeader>
					<AlertDialogTitle>{state.options.title || t("dialogTitle")}</AlertDialogTitle>
					{state.options.description ? <AlertDialogDescription>{state.options.description}</AlertDialogDescription> : null}
				</AlertDialogHeader>
				<AlertDialogFooter>
					<AlertDialogCancel onClick={handleCancel}>{state.options.cancelText}</AlertDialogCancel>
					<AlertDialogAction variant={state.options.tone === "destructive" ? "destructive" : "default"} onClick={handleConfirm}>
						{state.options.confirmText}
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);

	return {
		confirm,
		confirmDialogNode: dialogNode,
	};
}
