import { useEffect, useMemo, useState } from "react";
import Image from "next/image";
import QRCode from "qrcode";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { HandleText } from "@/components/ui/handle-text";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { isValidUsername, normalizeUsername } from "@/lib/app-helpers";
import { CopyIcon, KeyRoundIcon, Link2Icon, RefreshCwIcon, ShieldCheckIcon, ShieldOffIcon, SmartphoneIcon } from "lucide-react";
import { LanguageSwitcher } from "@/components/ui/language-switcher";
import { useTranslation } from "react-i18next";

type MfaStatus = {
	mfaEnabled: boolean;
	totpEnabled: boolean;
	passkeyCount: number;
	backupCodeCount: number;
};

type BackupCodeStatus = {
	activeCount: number;
	latestGeneratedAt: string | null;
};

type MfaSetup = {
	secretBase32: string;
	otpauthUrl: string;
};

type ProfileSummary = {
	id: string;
	name: string;
	avatarUrl?: string | null;
	isActive: boolean;
};

type PasskeyCredential = {
	credentialId: string;
	name: string | null;
	transports: string[];
	createdAt: string;
	updatedAt: string;
};

type SessionUser = {
	email: string;
	username: string;
	usernameDiscriminator: string;
	handle: string;
	isAdmin: boolean;
};

type SettingsPageProps = {
	sessionUser: SessionUser;
	enabledProfile: ProfileSummary | null;
	selectedProfile: ProfileSummary | null;
	profiles: ProfileSummary[];
	activeProfileId: string;
	activeProfileAvatarUrl: string | null;
	mfaStatus: MfaStatus | null;
	mfaSetup: MfaSetup | null;
	mfaSetupCode: string;
	passkeys: PasskeyCredential[];
	backupCodeStatus: BackupCodeStatus;
	onActiveProfileIdChange: (value: string) => void;
	onReloadProfiles: () => void | Promise<void>;
	onStartTotpSetup: () => void | Promise<void>;
	onDisableTotp: () => void | Promise<void>;
	onMfaSetupCodeChange: (value: string) => void;
	onConfirmTotpSetup: () => void | Promise<void>;
	onUpdateUsername: (username: string) => Promise<boolean>;
	onRegisterPasskey: () => Promise<string | null>;
	onRenamePasskey: (credentialId: string, name: string) => Promise<boolean>;
	onDeletePasskey: (credentialId: string) => Promise<void>;
	onRegenerateBackupCodes: () => Promise<(BackupCodeStatus & { codes: string[]; generatedAt: string }) | null>;
};

function formatDateTime(value: string | null | undefined, t: (key: string) => string) {
	if (!value) {
		return t("notGenerated");
	}
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) {
		return value;
	}
	return new Intl.DateTimeFormat("zh-CN", {
		dateStyle: "medium",
		timeStyle: "short",
	}).format(date);
}

function summarizeCredentialId(credentialId: string) {
	if (credentialId.length <= 18) {
		return credentialId;
	}
	return `${credentialId.slice(0, 9)}…${credentialId.slice(-6)}`;
}

function sanitizeBackupCode(value: string) {
	return value
		.trim()
		.toUpperCase()
		.replace(/[^A-Z0-9]/gu, "");
}

export function SettingsPage({
	sessionUser,
	enabledProfile,
	selectedProfile,
	profiles,
	activeProfileId,
	activeProfileAvatarUrl,
	mfaStatus,
	mfaSetup,
	mfaSetupCode,
	passkeys,
	backupCodeStatus,
	onActiveProfileIdChange,
	onReloadProfiles,
	onStartTotpSetup,
	onDisableTotp,
	onMfaSetupCodeChange,
	onConfirmTotpSetup,
	onUpdateUsername,
	onRegisterPasskey,
	onRenamePasskey,
	onDeletePasskey,
	onRegenerateBackupCodes,
}: SettingsPageProps) {
	const { t } = useTranslation("settings");
	const [totpQrDataUrl, setTotpQrDataUrl] = useState("");
	const [registeringPasskey, setRegisteringPasskey] = useState(false);
	const [renameDialogOpen, setRenameDialogOpen] = useState(false);
	const [renameTargetId, setRenameTargetId] = useState("");
	const [renameDraft, setRenameDraft] = useState("");
	const [renamingPasskey, setRenamingPasskey] = useState(false);
	const [backupCodes, setBackupCodes] = useState<string[]>([]);
	const [backupCodesGeneratedAt, setBackupCodesGeneratedAt] = useState<string | null>(null);
	const [generatingBackupCodes, setGeneratingBackupCodes] = useState(false);
	const [usernameDraft, setUsernameDraft] = useState(sessionUser.username);
	const [savingUsername, setSavingUsername] = useState(false);

	useEffect(() => {
		const otpauthUrl = mfaSetup?.otpauthUrl?.trim();
		if (!otpauthUrl) {
			setTotpQrDataUrl("");
			return;
		}

		let canceled = false;
		void QRCode.toDataURL(otpauthUrl, {
			errorCorrectionLevel: "medium",
			margin: 1,
			scale: 6,
		})
			.then((value) => {
				if (!canceled) {
					setTotpQrDataUrl(value);
				}
			})
			.catch(() => {
				if (!canceled) {
					setTotpQrDataUrl("");
				}
			});

		return () => {
			canceled = true;
		};
	}, [mfaSetup?.otpauthUrl]);

	useEffect(() => {
		setUsernameDraft(sessionUser.username);
	}, [sessionUser.username]);

	const passkeyNamedCount = useMemo(() => passkeys.filter((item) => Boolean(item.name?.trim())).length, [passkeys]);
	const normalizedUsernameDraft = normalizeUsername(usernameDraft);
	const usernameIsValid = isValidUsername(normalizedUsernameDraft);
	const usernameChanged =
		normalizedUsernameDraft !== normalizeUsername(sessionUser.username) || normalizedUsernameDraft !== sessionUser.username;
	const roleLabel = sessionUser.isAdmin ? t("roleAdmin") : t("roleUser");
	const handlePreview = usernameChanged ? `${normalizedUsernameDraft || sessionUser.username}#xxxx` : sessionUser.handle;

	const openRenameDialog = (credentialId: string, currentName?: string | null) => {
		setRenameTargetId(credentialId);
		setRenameDraft(currentName?.trim() ?? "");
		setRenameDialogOpen(true);
	};

	const handlePasskeyRegister = async () => {
		setRegisteringPasskey(true);
		try {
			const createdCredentialId = await onRegisterPasskey();
			if (createdCredentialId) {
				openRenameDialog(createdCredentialId);
			}
		} finally {
			setRegisteringPasskey(false);
		}
	};

	const handleRenameSubmit = async () => {
		if (!renameTargetId) {
			return;
		}
		setRenamingPasskey(true);
		try {
			const updated = await onRenamePasskey(renameTargetId, renameDraft);
			if (updated) {
				setRenameDialogOpen(false);
				setRenameTargetId("");
				setRenameDraft("");
			}
		} finally {
			setRenamingPasskey(false);
		}
	};

	const handleRegenerateCodes = async () => {
		setGeneratingBackupCodes(true);
		try {
			const payload = await onRegenerateBackupCodes();
			if (!payload) {
				return;
			}
			setBackupCodes(payload.codes);
			setBackupCodesGeneratedAt(payload.generatedAt);
		} finally {
			setGeneratingBackupCodes(false);
		}
	};

	const handleUsernameSave = async () => {
		if (!usernameIsValid || !usernameChanged) {
			return;
		}
		setSavingUsername(true);
		try {
			const updated = await onUpdateUsername(normalizedUsernameDraft);
			if (updated) {
				setUsernameDraft(normalizedUsernameDraft);
			}
		} finally {
			setSavingUsername(false);
		}
	};

	return (
		<Card size="sm">
			<CardHeader>
				<CardTitle>{t("title")}</CardTitle>
				<CardDescription>{t("desc")}</CardDescription>
			</CardHeader>
			<CardContent className="flex flex-col gap-4">
				<Tabs defaultValue="overview" className="w-full">
					<TabsList variant="line" className="w-full justify-start rounded-lg border border-border/70 bg-muted/20 p-1">
						<TabsTrigger value="overview">{t("tabOverview")}</TabsTrigger>
						<TabsTrigger value="security">{t("tabSecurity")}</TabsTrigger>
					</TabsList>

					<TabsContent value="overview" className="mt-4">
						<section className="rounded-xl border border-border/70 bg-card/30 p-4">
							<div className="flex items-center gap-3">
								<Avatar className="size-12 rounded-md">
									<AvatarImage src={activeProfileAvatarUrl ?? undefined} />
									<AvatarFallback>{sessionUser.handle.slice(0, 1).toUpperCase()}</AvatarFallback>
								</Avatar>
								<div className="min-w-0">
									<HandleText handle={sessionUser.handle} className="block truncate text-sm font-medium" />
									<p className="truncate text-xs text-muted-foreground">{sessionUser.email}</p>
									<div className="mt-2 flex flex-wrap gap-2">
										<Badge variant="secondary">{roleLabel}</Badge>
										<Badge variant="outline">{t("currentSessionIdentity")}</Badge>
									</div>
								</div>
							</div>

							<div className="mt-4 rounded-xl border border-border/70 p-4">
								<LanguageSwitcher />
							</div>
						</section>

						<section className="mt-4 rounded-xl border border-border/70 p-4">
							<h3 className="text-sm font-medium">{t("accountHandleTitle")}</h3>
							<p className="mt-1 text-sm text-muted-foreground">{t("accountHandleDesc")}</p>

							<div className="mt-4 grid gap-3 sm:grid-cols-2">
								<div className="rounded-lg border border-border/60 bg-muted/20 p-3">
									<p className="text-xs text-muted-foreground">{t("currentHandleLabel")}</p>
									<HandleText handle={sessionUser.handle} className="mt-1 block truncate text-sm font-medium" />
								</div>
								<div className="rounded-lg border border-border/60 bg-muted/20 p-3">
									<p className="text-xs text-muted-foreground">{t("handlePreviewLabel")}</p>
									<HandleText handle={handlePreview} className="mt-1 block truncate text-sm font-medium" />
								</div>
							</div>

							<FieldGroup className="mt-4 gap-3 md:flex-row md:items-end">
								<Field className="min-w-0 flex-1">
									<FieldLabel htmlFor="settings-username">{t("usernameLabel")}</FieldLabel>
									<Input
										id="settings-username"
										value={usernameDraft}
										onChange={(event) => setUsernameDraft(event.target.value)}
									/>
									<p className="mt-2 text-sm text-muted-foreground">{t("usernameHint")}</p>
									{normalizedUsernameDraft && !usernameIsValid ? (
										<p className="mt-2 text-sm text-destructive">{t("usernameInvalid")}</p>
									) : null}
								</Field>
								<Button
									className="w-full md:w-auto md:shrink-0"
									onClick={() => void handleUsernameSave()}
									disabled={savingUsername || !usernameChanged || !usernameIsValid}
								>
									{savingUsername ? t("saving") : t("saveHandle")}
								</Button>
							</FieldGroup>
						</section>

						<section className="mt-4 rounded-xl border border-border/70 p-4">
							<h3 className="text-sm font-medium">{t("profileScopeTitle")}</h3>
							<p className="mt-1 text-sm text-muted-foreground">{t("profileScopeDesc")}</p>

							<div className="mt-4 flex flex-col gap-4">
								<div className="rounded-lg border border-border/60 bg-muted/20 p-3">
									<p className="text-xs text-muted-foreground">{t("currentViewProfile")}</p>
									<p className="mt-1 truncate text-sm font-medium">{selectedProfile?.name ?? t("unselected")}</p>
									<p className="truncate text-xs text-muted-foreground">
										{selectedProfile?.id ?? t("pleaseSelectViewProfile")}
									</p>
								</div>

								<FieldGroup className="gap-2 rounded-lg border border-border/60 p-3">
									<Field>
										<FieldLabel>{t("switchViewProfile")}</FieldLabel>
										<Select value={activeProfileId} onValueChange={onActiveProfileIdChange}>
											<SelectTrigger className="w-full">
												<SelectValue placeholder={t("selectViewProfile")} />
											</SelectTrigger>
											<SelectContent>
												<SelectGroup>
													{profiles.map((profile) => (
														<SelectItem key={profile.id} value={profile.id}>
															{profile.name} {profile.isActive ? ` ${t("iosActiveLabel")}` : ""}
														</SelectItem>
													))}
												</SelectGroup>
											</SelectContent>
										</Select>
									</Field>
								</FieldGroup>

								<div className="rounded-lg border border-border/60 bg-muted/20 p-3">
									<p className="text-xs text-muted-foreground">{t("iosEnabledProfile")}</p>
									<p className="mt-1 truncate text-sm font-medium">{enabledProfile?.name ?? t("noEnabledProfile")}</p>
									<p className="truncate text-xs text-muted-foreground">{enabledProfile?.id ?? t("manageOnIos")}</p>
								</div>
							</div>

							<div className="mt-4">
								<Button size="sm" variant="outline" onClick={() => void onReloadProfiles()}>
									<RefreshCwIcon data-icon="inline-start" />
									{t("refreshProfiles")}
								</Button>
							</div>
						</section>
					</TabsContent>

					<TabsContent value="security" className="mt-4">
						<section className="rounded-xl border border-border/70 p-4">
							<h3 className="text-sm font-medium">{t("securityOverview")}</h3>
							<div className="mt-3 flex flex-wrap gap-2">
								<Badge variant="secondary">MFA: {mfaStatus?.mfaEnabled ? "Enabled" : "Disabled"}</Badge>
								<Badge variant="secondary">TOTP: {mfaStatus?.totpEnabled ? "On" : "Off"}</Badge>
								<Badge variant="secondary">Passkey: {mfaStatus?.passkeyCount ?? 0}</Badge>
								<Badge variant="secondary">Backup Codes: {backupCodeStatus.activeCount}</Badge>
							</div>
						</section>

						<section className="mt-4 rounded-xl border border-border/70 p-4">
							<div className="mb-3 flex items-start justify-between gap-3">
								<div>
									<h3 className="text-sm font-medium">{t("totpTitle")}</h3>
									<p className="text-sm text-muted-foreground">{t("totpDesc")}</p>
								</div>
								<div className="flex w-full flex-wrap gap-2 md:w-auto">
									<Button className="h-9 w-full sm:w-auto" size="sm" variant="outline" onClick={() => void onStartTotpSetup()}>
										<ShieldCheckIcon data-icon="inline-start" />
										{t("generateTotpKey")}
									</Button>
									<Button className="h-9 w-full sm:w-auto" size="sm" variant="outline" onClick={() => void onDisableTotp()}>
										<ShieldOffIcon data-icon="inline-start" />
										{t("disableTotp")}
									</Button>
								</div>
							</div>

							{mfaSetup ? (
								<div className="rounded-lg border border-border/60 bg-muted/20 p-3">
									<div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_220px]">
										<div className="space-y-3">
											<Field>
												<FieldLabel htmlFor="totp-uri">{t("otpLink")}</FieldLabel>
												<Input id="totp-uri" value={mfaSetup.otpauthUrl} readOnly />
											</Field>
											<div className="flex flex-wrap gap-2">
												<Button size="sm" variant="outline" asChild>
													<a href={mfaSetup.otpauthUrl}>
														<Link2Icon data-icon="inline-start" />
														{t("openOtpLink")}
													</a>
												</Button>
												<Button
													size="sm"
													variant="outline"
													onClick={() => void navigator.clipboard?.writeText(mfaSetup.otpauthUrl)}
												>
													<CopyIcon data-icon="inline-start" />
													{t("copyLink")}
												</Button>
											</div>

											<Field>
												<FieldLabel htmlFor="totp-code">{t("verificationCode")}</FieldLabel>
												<Input
													id="totp-code"
													value={mfaSetupCode}
													onChange={(event) => onMfaSetupCodeChange(event.target.value)}
												/>
											</Field>
											<Button size="sm" onClick={() => void onConfirmTotpSetup()}>
												{t("confirmEnable")}
											</Button>
										</div>

										<div className="mx-auto flex w-[220px] flex-col items-center gap-2">
											<div className="flex h-[220px] w-[220px] items-center justify-center rounded-lg border border-border/60 bg-background p-2">
												{totpQrDataUrl ? (
													<Image
														src={totpQrDataUrl}
														alt="TOTP QR Code"
														width={220}
														height={220}
														unoptimized
														className="h-full w-full rounded-md object-contain"
													/>
												) : (
													<div className="text-center text-xs text-muted-foreground">{t("generatingQr")}</div>
												)}
											</div>
											<p className="text-xs text-muted-foreground">{t("scanQrDesc")}</p>
										</div>
									</div>
								</div>
							) : null}

							<div className="mt-4 rounded-lg border border-border/60 p-3">
								<div className="flex flex-wrap items-start justify-between gap-3">
									<div>
										<p className="text-sm font-medium">{t("totpBackupCodes")}</p>
										<p className="text-xs text-muted-foreground">
											{t("backupCodesStatus", {
												activeCount: backupCodeStatus.activeCount,
												latestGeneratedAt: formatDateTime(backupCodeStatus.latestGeneratedAt, t),
											})}
										</p>
									</div>
									<Button
										size="sm"
										variant="outline"
										disabled={generatingBackupCodes || !mfaStatus?.totpEnabled}
										onClick={() => void handleRegenerateCodes()}
									>
										{generatingBackupCodes ? t("generating") : t("regenerateBackupCodes")}
									</Button>
								</div>

								{backupCodes.length > 0 ? (
									<div className="mt-3 rounded-lg border border-border/60 bg-muted/20 p-3">
										<div className="mb-2 flex flex-wrap items-center justify-between gap-2">
											<p className="text-xs text-muted-foreground">
												{t("newBackupCodesDesc", { generatedAt: formatDateTime(backupCodesGeneratedAt, t) })}
											</p>
											<Button
												size="sm"
												variant="outline"
												onClick={() => void navigator.clipboard?.writeText(backupCodes.join("\n"))}
											>
												<CopyIcon data-icon="inline-start" />
												{t("copyAll")}
											</Button>
										</div>
										<div className="grid gap-2 sm:grid-cols-2">
											{backupCodes.map((code) => (
												<div
													key={code}
													className="rounded-md border border-border/60 bg-background px-2 py-1.5 font-mono text-sm"
												>
													{code}
												</div>
											))}
										</div>
										<p className="mt-2 text-xs text-muted-foreground">
											{t("backupCodeUsage1")}
											<code>{backupCodes[0] ?? "ABCD-EFGH"}</code>
											{t("backupCodeUsage2")}
											<code>{sanitizeBackupCode(backupCodes[0] ?? "ABCD-EFGH")}</code>。
										</p>
									</div>
								) : null}
							</div>
						</section>

						<section className="mt-4 rounded-xl border border-border/70 p-4">
							<div className="mb-3 flex items-start justify-between gap-3">
								<div>
									<h3 className="text-sm font-medium">{t("passkeyTitle")}</h3>
									<p className="text-sm text-muted-foreground">
										{t("passkeyDesc", { namedCount: passkeyNamedCount, totalCount: passkeys.length })}
									</p>
								</div>
								<Button size="sm" onClick={() => void handlePasskeyRegister()} disabled={registeringPasskey}>
									<SmartphoneIcon data-icon="inline-start" />
									{registeringPasskey ? t("registering") : t("registerPasskey")}
								</Button>
							</div>

							{passkeys.length === 0 ? (
								<div className="rounded-lg border border-dashed border-border/70 p-4 text-sm text-muted-foreground">
									{t("noPasskeyDesc")}
								</div>
							) : (
								<div className="rounded-lg border border-border/60">
									{passkeys.map((item, index) => (
										<div
											key={item.credentialId}
											className={[
												"flex flex-wrap items-center justify-between gap-3 p-3",
												index < passkeys.length - 1 ? "border-b border-border/60" : "",
											].join(" ")}
										>
											<div className="min-w-0">
												<p className="truncate text-sm font-medium">{item.name?.trim() || t("unnamedPasskey")}</p>
												<p className="truncate text-xs text-muted-foreground">
													<KeyRoundIcon data-icon="inline-start" /> {summarizeCredentialId(item.credentialId)}
													{t("lastUpdated", { updatedAt: formatDateTime(item.updatedAt, t) })}
												</p>
											</div>
											<div className="flex flex-wrap gap-2">
												<Button size="sm" variant="outline" onClick={() => openRenameDialog(item.credentialId, item.name)}>
													{item.name?.trim() ? t("renameDevice") : t("nameDevice")}
												</Button>
												<Button size="sm" variant="outline" onClick={() => void onDeletePasskey(item.credentialId)}>
													{t("deleteDevice")}
												</Button>
											</div>
										</div>
									))}
								</div>
							)}
						</section>
					</TabsContent>
				</Tabs>
			</CardContent>

			<Dialog
				open={renameDialogOpen}
				onOpenChange={(open) => {
					setRenameDialogOpen(open);
					if (!open) {
						setRenameTargetId("");
						setRenameDraft("");
					}
				}}
			>
				<DialogContent>
					<DialogHeader>
						<DialogTitle>{t("renamePasskeyTitle")}</DialogTitle>
						<DialogDescription>{t("renamePasskeyDesc")}</DialogDescription>
					</DialogHeader>

					<Field>
						<FieldLabel htmlFor="passkey-name">{t("nameLabel")}</FieldLabel>
						<Input
							id="passkey-name"
							value={renameDraft}
							maxLength={64}
							onChange={(event) => setRenameDraft(event.target.value)}
							placeholder={t("inputDeviceName")}
						/>
					</Field>

					<DialogFooter>
						<Button variant="outline" onClick={() => setRenameDialogOpen(false)}>
							{t("maybeLater")}
						</Button>
						<Button onClick={() => void handleRenameSubmit()} disabled={renamingPasskey}>
							{renamingPasskey ? t("saving") : t("saveName")}
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</Card>
	);
}
