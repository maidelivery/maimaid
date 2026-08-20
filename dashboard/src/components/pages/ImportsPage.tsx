import { useState } from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { ExternalLinkIcon, UploadIcon } from "lucide-react";
import { DIVING_FISH_OAUTH_CLIENT_ID, LXNS_OAUTH_CLIENT_ID } from "@/lib/app-helpers";
import { useTranslation } from "react-i18next";

type ImportsPageProps = {
	lxnsAuthCode: string;
	onLxnsAuthCodeChange: (value: string) => void;
	onAuthorizeDf: () => void | Promise<void>;
	onImportDf: () => void | Promise<void>;
	onImportLxns: (input: { codeVerifier: string }) => void | Promise<void>;
};

const LXNS_OAUTH_REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob";
const LXNS_OAUTH_SCOPE = "read_user_profile read_player write_player read_user_token";

function base64UrlEncode(bytes: Uint8Array) {
	const binary = String.fromCharCode(...bytes);
	return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function generateCodeVerifier(length = 64) {
	const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
	const random = new Uint8Array(length);
	crypto.getRandomValues(random);
	return Array.from(random, (value) => alphabet[value % alphabet.length] ?? "A").join("");
}

async function generateCodeChallenge(codeVerifier: string) {
	const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(codeVerifier));
	return base64UrlEncode(new Uint8Array(digest));
}

export function ImportsPage({ lxnsAuthCode, onLxnsAuthCodeChange, onAuthorizeDf, onImportDf, onImportLxns }: ImportsPageProps) {
	const { t } = useTranslation("imports");
	const [lxnsCodeVerifier, setLxnsCodeVerifier] = useState("");
	const [isPreparingDfOauth, setIsPreparingDfOauth] = useState(false);
	const [isPreparingLxnsOauth, setIsPreparingLxnsOauth] = useState(false);
	const hasDivingFishClientId = DIVING_FISH_OAUTH_CLIENT_ID.length > 0;
	const hasLxnsClientId = LXNS_OAUTH_CLIENT_ID.length > 0;

	const handleOpenLxnsOauth = async () => {
		if (!hasLxnsClientId) {
			return;
		}

		try {
			setIsPreparingLxnsOauth(true);
			const verifier = generateCodeVerifier();
			const challenge = await generateCodeChallenge(verifier);
			setLxnsCodeVerifier(verifier);

			const params = new URLSearchParams({
				response_type: "code",
				client_id: LXNS_OAUTH_CLIENT_ID,
				redirect_uri: LXNS_OAUTH_REDIRECT_URI,
				scope: LXNS_OAUTH_SCOPE,
				code_challenge: challenge,
				code_challenge_method: "S256",
				state: crypto.randomUUID(),
			});
			window.open(`https://maimai.lxns.net/oauth/authorize?${params.toString()}`, "_blank", "noopener,noreferrer");
		} finally {
			setIsPreparingLxnsOauth(false);
		}
	};

	const handleOpenDfOauth = async () => {
		if (!hasDivingFishClientId) {
			return;
		}
		try {
			setIsPreparingDfOauth(true);
			await onAuthorizeDf();
		} finally {
			setIsPreparingDfOauth(false);
		}
	};

	return (
		<div className="flex min-w-0 flex-col gap-4">
			<p className="text-sm text-muted-foreground">{t("pageDesc")}</p>

			<Alert>
				<AlertTitle>{t("alertTitleInfo")}</AlertTitle>
				<AlertDescription>{t("alertDescInfo")}</AlertDescription>
			</Alert>
			{!hasDivingFishClientId || !hasLxnsClientId ? (
				<Alert variant="destructive">
					<AlertTitle>{t("alertTitleError")}</AlertTitle>
					<AlertDescription>{t("alertDescError")}</AlertDescription>
				</Alert>
			) : null}

			<Card size="sm">
				<CardHeader>
					<CardTitle>{t("sectionDf")}</CardTitle>
				</CardHeader>
				<CardContent>
					<p className="text-sm text-muted-foreground">{t("dfOauthDesc")}</p>
					<div className="mt-3 flex flex-wrap gap-2">
						<Button
							variant="outline"
							disabled={isPreparingDfOauth || !hasDivingFishClientId}
							onClick={() => void handleOpenDfOauth()}
						>
							<ExternalLinkIcon data-icon="inline-start" />
							{t("btnAuthorizeDf")}
						</Button>
						<Button onClick={() => void onImportDf()}>
							<UploadIcon data-icon="inline-start" />
							{t("btnImportDf")}
						</Button>
					</div>
				</CardContent>
			</Card>

			<Card size="sm">
				<CardHeader>
					<CardTitle>{t("sectionLxns")}</CardTitle>
				</CardHeader>
				<CardContent>
					<FieldGroup>
						<Field>
							<FieldLabel>{t("labelLxnsAuth")}</FieldLabel>
							<Button
								className="w-fit"
								variant="outline"
								disabled={isPreparingLxnsOauth || !hasLxnsClientId}
								onClick={() => void handleOpenLxnsOauth()}
							>
								<ExternalLinkIcon data-icon="inline-start" />
								{t("btnOpenAuth")}
							</Button>
						</Field>
						<Field>
							<FieldLabel htmlFor="lxns-auth-code">{t("labelAuthCode")}</FieldLabel>
							<Input id="lxns-auth-code" value={lxnsAuthCode} onChange={(event) => onLxnsAuthCodeChange(event.target.value)} />
						</Field>
					</FieldGroup>
					<Button
						className="mt-3"
						disabled={!hasLxnsClientId || !lxnsAuthCode.trim() || !lxnsCodeVerifier}
						onClick={() => void onImportLxns({ codeVerifier: lxnsCodeVerifier })}
					>
						<UploadIcon data-icon="inline-start" />
						{t("btnImportLxns")}
					</Button>
				</CardContent>
			</Card>
		</div>
	);
}
