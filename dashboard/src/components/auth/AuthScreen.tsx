import { type Dispatch, type SetStateAction, useEffect, useState } from "react";
import { Loader2Icon } from "lucide-react";
import { REGEXP_ONLY_DIGITS } from "input-otp";
import { Avatar as UiAvatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { InputOTP, InputOTPGroup, InputOTPSlot } from "@/components/ui/input-otp";
import { Toaster } from "@/components/ui/sonner";
import { cn } from "@/lib/utils";
import { PASSWORD_COMPLEXITY_HINT } from "@/lib/app-helpers";
import type { AuthMode, LoginStep, VerificationResult } from "@/lib/app-types";
import { useTranslation } from "react-i18next";

type AuthScreenProps = {
	authMode: AuthMode;
	setAuthMode: Dispatch<SetStateAction<AuthMode>>;
	loginStep: LoginStep;
	loginEmail: string;
	setLoginEmail: Dispatch<SetStateAction<string>>;
	loginPassword: string;
	setLoginPassword: Dispatch<SetStateAction<string>>;
	registerEmail: string;
	setRegisterEmail: Dispatch<SetStateAction<string>>;
	registerPassword: string;
	setRegisterPassword: Dispatch<SetStateAction<string>>;
	registerConfirmPassword: string;
	setRegisterConfirmPassword: Dispatch<SetStateAction<string>>;
	forgotEmail: string;
	setForgotEmail: Dispatch<SetStateAction<string>>;
	forgotResultMessage: string | null;
	setForgotResultMessage: Dispatch<SetStateAction<string | null>>;
	verificationEmail: string;
	verificationEmailSent: boolean | null;
	verificationResult: VerificationResult | null;
	setVerificationResult: Dispatch<SetStateAction<VerificationResult | null>>;
	resetEmail: string;
	setResetEmail: Dispatch<SetStateAction<string>>;
	resetPassword: string;
	setResetPassword: Dispatch<SetStateAction<string>>;
	resetConfirmPassword: string;
	setResetConfirmPassword: Dispatch<SetStateAction<string>>;
	resetResultMessage: string | null;
	setResetResultMessage: Dispatch<SetStateAction<string | null>>;
	setResetToken: Dispatch<SetStateAction<string>>;
	loading: boolean;
	mfaMethods: {
		totp: boolean;
		passkey: boolean;
		backupCode: boolean;
	};
	mfaTotpCode: string;
	setMfaTotpCode: Dispatch<SetStateAction<string>>;
	mfaBackupCode: string;
	setMfaBackupCode: Dispatch<SetStateAction<string>>;
	resetLoginFlow: () => void;
	handleLoginContinue: () => Promise<void>;
	handleLoginWithPassword: () => Promise<void>;
	handleTotpChallenge: () => Promise<void>;
	handlePasskeyChallenge: () => Promise<void>;
	handleBackupCodeChallenge: () => Promise<void>;
	handleDirectPasskeyLogin: () => Promise<void>;
	handleRegister: () => Promise<void>;
	handleResendVerification: () => Promise<void>;
	handleForgotPassword: () => Promise<void>;
	handleResetPassword: () => Promise<void>;
};

export function AuthScreen(props: AuthScreenProps) {
	const {
		authMode,
		setAuthMode,
		loginStep,
		loginEmail,
		setLoginEmail,
		loginPassword,
		setLoginPassword,
		registerEmail,
		setRegisterEmail,
		registerPassword,
		setRegisterPassword,
		registerConfirmPassword,
		setRegisterConfirmPassword,
		forgotEmail,
		setForgotEmail,
		forgotResultMessage,
		setForgotResultMessage,
		verificationEmail,
		verificationEmailSent,
		verificationResult,
		setVerificationResult,
		resetEmail,
		setResetEmail,
		resetPassword,
		setResetPassword,
		resetConfirmPassword,
		setResetConfirmPassword,
		resetResultMessage,
		setResetResultMessage,
		setResetToken,
		loading,
		mfaMethods,
		mfaTotpCode,
		setMfaTotpCode,
		mfaBackupCode,
		setMfaBackupCode,
		resetLoginFlow,
		handleLoginContinue,
		handleLoginWithPassword,
		handleTotpChallenge,
		handlePasskeyChallenge,
		handleBackupCodeChallenge,
		handleDirectPasskeyLogin,
		handleRegister,
		handleResendVerification,
		handleForgotPassword,
		handleResetPassword,
	} = props;

	const { t } = useTranslation();

	const normalizedBackupCode = mfaBackupCode
		.trim()
		.toUpperCase()
		.replace(/[^A-Z0-9]/gu, "");
	const [isMobileViewport, setIsMobileViewport] = useState(false);
	const toasterPosition = isMobileViewport ? "bottom-center" : "top-right";

	useEffect(() => {
		if (typeof window === "undefined") {
			return;
		}
		const mediaQuery = window.matchMedia("(max-width: 767px)");
		const syncViewport = () => {
			setIsMobileViewport(mediaQuery.matches);
		};
		syncViewport();
		mediaQuery.addEventListener("change", syncViewport);
		return () => {
			mediaQuery.removeEventListener("change", syncViewport);
		};
	}, []);

	return (
		<div className="min-h-screen bg-background text-foreground">
			<main className="mx-auto flex min-h-screen w-full max-w-md flex-col items-center justify-center gap-5 px-4 py-10">
				<div className="flex w-full items-center gap-3 px-1">
					<UiAvatar className="size-8 border border-border/70 bg-card">
						<AvatarFallback className="text-xs font-semibold">MD</AvatarFallback>
					</UiAvatar>
					<p className="text-xl font-semibold">maimaid Dashboard</p>
				</div>

				<Card className="w-full border-border/60 bg-card/90 backdrop-blur">
					<CardHeader>
						<CardTitle>
							{authMode === "register"
								? t("auth:titleRegister")
								: authMode === "forgot"
									? t("auth:titleForgot")
									: authMode === "reset-password"
										? t("auth:titleReset")
										: authMode === "verify-email"
											? t("auth:titleVerify")
											: t("auth:titleLogin")}
						</CardTitle>
						<CardDescription>
							{authMode === "register"
								? t("auth:descRegister")
								: authMode === "forgot"
									? t("auth:descForgot")
									: authMode === "reset-password"
										? t("auth:descReset")
										: authMode === "verify-email"
											? t("auth:descVerify")
											: loginStep === "email"
												? t("auth:descLoginEmail")
												: loginStep === "password"
													? t("auth:descLoginPwd")
													: t("auth:descLoginMfa")}
						</CardDescription>
					</CardHeader>
					<CardContent className="flex flex-col gap-4">
						{authMode === "login" ? (
							<>
								<FieldGroup>
									<Field>
										<FieldLabel htmlFor="login-email">{t("auth:email")}</FieldLabel>
										<Input
											id="login-email"
											type="email"
											value={loginEmail}
											onChange={(event) => {
												const nextEmail = event.target.value;
												setLoginEmail(nextEmail);
												if (loginStep !== "email") {
													resetLoginFlow();
												}
											}}
											readOnly={loginStep === "mfa"}
										/>
									</Field>
									{loginStep === "password" ? (
										<Field>
											<FieldLabel htmlFor="login-password">{t("auth:password")}</FieldLabel>
											<Input
												id="login-password"
												type="password"
												value={loginPassword}
												onChange={(event) => setLoginPassword(event.target.value)}
											/>
										</Field>
									) : null}
									{loginStep === "mfa" && mfaMethods.totp ? (
										<Field>
											<FieldLabel htmlFor="login-totp">{t("auth:mfaCode")}</FieldLabel>
											<InputOTP
												id="login-totp"
												value={mfaTotpCode}
												maxLength={6}
												pattern={REGEXP_ONLY_DIGITS}
												onChange={setMfaTotpCode}
											>
												<InputOTPGroup>
													<InputOTPSlot index={0} />
													<InputOTPSlot index={1} />
													<InputOTPSlot index={2} />
													<InputOTPSlot index={3} />
													<InputOTPSlot index={4} />
													<InputOTPSlot index={5} />
												</InputOTPGroup>
											</InputOTP>
										</Field>
									) : null}
									{loginStep === "mfa" && mfaMethods.backupCode ? (
										<Field>
											<FieldLabel htmlFor="login-backup-code">{t("auth:mfaBackup")}</FieldLabel>
											<Input
												id="login-backup-code"
												value={mfaBackupCode}
												onChange={(event) => setMfaBackupCode(event.target.value)}
												placeholder={t("auth:mfaBackupPh")}
											/>
										</Field>
									) : null}
								</FieldGroup>

								{loginStep === "email" ? (
									<Button onClick={() => void handleLoginContinue()} disabled={loading}>
										{loading ? <Loader2Icon data-icon="inline-start" className="animate-spin" /> : null}
										{loading ? t("auth:btnProcessing") : t("auth:btnContinue")}
									</Button>
								) : null}
								{loginStep === "password" ? (
									<>
										<Button onClick={() => void handleLoginWithPassword()} disabled={loading}>
											{loading ? <Loader2Icon data-icon="inline-start" className="animate-spin" /> : null}
											{loading ? t("auth:btnLoggingIn") : t("auth:btnLogin")}
										</Button>
										<Button
											variant="ghost"
											onClick={() => {
												resetLoginFlow();
											}}
											disabled={loading}
										>
											{t("auth:btnBackEmail")}
										</Button>
									</>
								) : null}
								{loginStep === "mfa" ? (
									<>
										{mfaMethods.totp ? (
											<Button onClick={() => void handleTotpChallenge()} disabled={loading || mfaTotpCode.trim().length !== 6}>
												{loading ? <Loader2Icon data-icon="inline-start" className="animate-spin" /> : null}
												{t("auth:btnVerifyLogin")}
											</Button>
										) : null}
										{mfaMethods.backupCode ? (
											<Button
												variant="outline"
												onClick={() => void handleBackupCodeChallenge()}
												disabled={loading || normalizedBackupCode.length !== 8}
											>
												{t("auth:btnBackupLogin")}
											</Button>
										) : null}
										{mfaMethods.passkey && !mfaMethods.totp ? (
											<Button variant="outline" onClick={() => void handlePasskeyChallenge()} disabled={loading}>
												{t("auth:btnPasskeyVerify")}
											</Button>
										) : null}
										<Button
											variant="ghost"
											onClick={() => {
												resetLoginFlow();
											}}
											disabled={loading}
										>
											{t("auth:btnBackEmail")}
										</Button>
									</>
								) : null}

								{loginStep === "email" ? (
									<Button variant="outline" onClick={() => void handleDirectPasskeyLogin()} disabled={loading}>
										{t("auth:btnPasskeyLogin")}
									</Button>
								) : null}

								<div className="flex items-center justify-between text-sm text-muted-foreground">
									<button
										type="button"
										className="underline-offset-4 hover:text-foreground hover:underline"
										onClick={() => {
											setVerificationResult(null);
											setAuthMode("forgot");
											setForgotEmail(loginEmail.trim().toLowerCase());
											setForgotResultMessage(null);
										}}
									>
										{t("auth:linkForgot")}
									</button>
									<button
										type="button"
										className="underline-offset-4 hover:text-foreground hover:underline"
										onClick={() => {
											setVerificationResult(null);
											setAuthMode("register");
											setRegisterEmail(loginEmail.trim().toLowerCase());
										}}
									>
										{t("auth:linkSignUp")}
									</button>
								</div>
							</>
						) : null}

						{authMode === "register" ? (
							<>
								<FieldGroup>
									<Field>
										<FieldLabel htmlFor="register-email">{t("auth:email")}</FieldLabel>
										<Input
											id="register-email"
											type="email"
											value={registerEmail}
											onChange={(event) => setRegisterEmail(event.target.value)}
										/>
									</Field>
									<Field>
										<FieldLabel htmlFor="register-password">{t("auth:password")}</FieldLabel>
										<Input
											id="register-password"
											type="password"
											value={registerPassword}
											onChange={(event) => setRegisterPassword(event.target.value)}
										/>
										<p className="mt-2 text-sm text-muted-foreground">{PASSWORD_COMPLEXITY_HINT}</p>
									</Field>
									<Field>
										<FieldLabel htmlFor="register-confirm-password">{t("auth:confirmPassword")}</FieldLabel>
										<Input
											id="register-confirm-password"
											type="password"
											value={registerConfirmPassword}
											onChange={(event) => setRegisterConfirmPassword(event.target.value)}
										/>
									</Field>
								</FieldGroup>
								<Button onClick={() => void handleRegister()} disabled={loading}>
									{loading ? <Loader2Icon data-icon="inline-start" className="animate-spin" /> : null}
									{t("auth:btnCreateAccount")}
								</Button>
								<button
									type="button"
									className="text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
									onClick={() => {
										setVerificationResult(null);
										setAuthMode("login");
										resetLoginFlow();
									}}
								>
									{t("auth:linkSignIn")}
								</button>
							</>
						) : null}

						{authMode === "forgot" ? (
							<>
								<FieldGroup>
									<Field>
										<FieldLabel htmlFor="forgot-email">{t("auth:email")}</FieldLabel>
										<Input
											id="forgot-email"
											type="email"
											value={forgotEmail}
											onChange={(event) => setForgotEmail(event.target.value)}
										/>
									</Field>
								</FieldGroup>
								{forgotResultMessage ? <p className="text-sm text-muted-foreground">{forgotResultMessage}</p> : null}
								<Button onClick={() => void handleForgotPassword()} disabled={loading}>
									{loading ? <Loader2Icon data-icon="inline-start" className="animate-spin" /> : null}
									{t("auth:btnSendReset")}
								</Button>
								<button
									type="button"
									className="text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
									onClick={() => {
										setVerificationResult(null);
										setAuthMode("login");
										resetLoginFlow();
									}}
								>
									{t("auth:linkBackSignIn")}
								</button>
							</>
						) : null}

						{authMode === "reset-password" ? (
							<>
								<FieldGroup>
									<Field>
										<FieldLabel htmlFor="reset-email">{t("auth:email")}</FieldLabel>
										<Input
											id="reset-email"
											type="email"
											value={resetEmail}
											onChange={(event) => setResetEmail(event.target.value)}
											disabled={Boolean(resetResultMessage)}
										/>
									</Field>
									<Field>
										<FieldLabel htmlFor="reset-password">{t("auth:password")}</FieldLabel>
										<Input
											id="reset-password"
											type="password"
											value={resetPassword}
											onChange={(event) => setResetPassword(event.target.value)}
											disabled={Boolean(resetResultMessage)}
										/>
										<p className="mt-2 text-sm text-muted-foreground">{PASSWORD_COMPLEXITY_HINT}</p>
									</Field>
									<Field>
										<FieldLabel htmlFor="reset-confirm-password">{t("auth:confirmPassword")}</FieldLabel>
										<Input
											id="reset-confirm-password"
											type="password"
											value={resetConfirmPassword}
											onChange={(event) => setResetConfirmPassword(event.target.value)}
											disabled={Boolean(resetResultMessage)}
										/>
									</Field>
								</FieldGroup>
								{resetResultMessage ? <p className="text-sm text-emerald-500">{resetResultMessage}</p> : null}
								{!resetResultMessage ? (
									<Button onClick={() => void handleResetPassword()} disabled={loading}>
										{loading ? <Loader2Icon data-icon="inline-start" className="animate-spin" /> : null}
										{t("auth:btnSubmitNewPwd")}
									</Button>
								) : null}
								<Button
									variant={resetResultMessage ? "default" : "ghost"}
									onClick={() => {
										setVerificationResult(null);
										setResetResultMessage(null);
										setResetPassword("");
										setResetConfirmPassword("");
										setResetToken("");
										setAuthMode("login");
										resetLoginFlow();
									}}
									disabled={loading}
								>
									{t("auth:linkBackSignIn")}
								</Button>
							</>
						) : null}

						{authMode === "verify-email" ? (
							<>
								{verificationResult ? (
									<p className={cn("text-sm", verificationResult.status === "success" ? "text-emerald-500" : "text-red-500")}>
										{verificationResult.status === "success" ? t("auth:verifySuccess") : t("auth:verifyExpired")}
									</p>
								) : (
									<>
										<p className="text-sm text-muted-foreground">
											{t("auth:verifyCreated1")}
											{verificationEmail}
											{t("auth:verifyCreated2")}
										</p>
										<p className={cn("text-sm", verificationEmailSent ? "text-emerald-500" : "text-yellow-500")}>
											{verificationEmailSent ? t("auth:verifySent") : t("auth:verifyFailSend")}
										</p>
										<Button variant="outline" onClick={() => void handleResendVerification()} disabled={loading}>
											{loading ? <Loader2Icon data-icon="inline-start" className="animate-spin" /> : null}
											{t("auth:btnResendVerify")}
										</Button>
									</>
								)}
								<Button
									onClick={() => {
										setVerificationResult(null);
										setAuthMode("login");
										if (verificationEmail.trim()) {
											setLoginEmail(verificationEmail);
										}
										resetLoginFlow();
									}}
									disabled={loading}
								>
									{t("auth:btnGoLogin")}
								</Button>
							</>
						) : null}
					</CardContent>
				</Card>

				<Toaster position={toasterPosition} richColors />
			</main>
		</div>
	);
}
