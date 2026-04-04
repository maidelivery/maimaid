import { useState } from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { ExternalLinkIcon, UploadIcon } from "lucide-react";
import { LXNS_OAUTH_CLIENT_ID } from "@/lib/app-helpers";
import { useTranslation } from "react-i18next";

type ImportsPageProps = {
  dfQQ: string;
  dfImportToken: string;
  lxnsAuthCode: string;
  onDfQQChange: (value: string) => void;
  onDfImportTokenChange: (value: string) => void;
  onLxnsAuthCodeChange: (value: string) => void;
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

export function ImportsPage({
  dfQQ,
  dfImportToken,
  lxnsAuthCode,
  onDfQQChange,
  onDfImportTokenChange,
  onLxnsAuthCodeChange,
  onImportDf,
  onImportLxns,
}: ImportsPageProps) {
  const { t } = useTranslation("imports");
  const [lxnsCodeVerifier, setLxnsCodeVerifier] = useState("");
  const [isPreparingLxnsOauth, setIsPreparingLxnsOauth] = useState(false);
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

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("pageTitle")}</CardTitle>
        <CardDescription>{t("pageDesc")}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        <Alert>
           <AlertTitle>{t("alertTitleInfo")}</AlertTitle>
          <AlertDescription>{t("alertDescInfo")}</AlertDescription>
        </Alert>
        {!hasLxnsClientId ? (
          <Alert variant="destructive">
             <AlertTitle>{t("alertTitleError")}</AlertTitle>
            <AlertDescription>{t("alertDescError")}</AlertDescription>
          </Alert>
        ) : null}

        <section className="rounded-lg border p-4">
           <h3 className="mb-3 text-sm font-medium">{t("sectionDf")}</h3>
          <FieldGroup>
            <Field>
               <FieldLabel htmlFor="df-qq">{t("labelQq")}</FieldLabel>
              <Input id="df-qq" value={dfQQ} onChange={(event) => onDfQQChange(event.target.value)} />
            </Field>
            <Field>
               <FieldLabel htmlFor="df-import-token">{t("labelImportToken")}</FieldLabel>
              <Input
                id="df-import-token"
                type="password"
                value={dfImportToken}
                onChange={(event) => onDfImportTokenChange(event.target.value)}
              />
            </Field>
          </FieldGroup>
          <Button className="mt-3" disabled={!dfQQ.trim() || !dfImportToken.trim()} onClick={() => void onImportDf()}>
            <UploadIcon data-icon="inline-start" />
            {t("btnImportDf")}
          </Button>
        </section>

        <section className="rounded-lg border p-4">
           <h3 className="mb-3 text-sm font-medium">{t("sectionLxns")}</h3>
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
              <Input
                id="lxns-auth-code"
                value={lxnsAuthCode}
                onChange={(event) => onLxnsAuthCodeChange(event.target.value)}
              />
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
        </section>
      </CardContent>
    </Card>
  );
}
