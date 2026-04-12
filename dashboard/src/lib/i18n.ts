import i18n from "i18next";
import { initReactI18next } from "react-i18next";

import zhHans from "../locales/zh-Hans.json";
import zhHant from "../locales/zh-Hant.json";
import en from "../locales/en.json";
import ja from "../locales/ja.json";

export const SUPPORTED_LANGUAGES = ["zh-Hans", "zh-Hant", "en", "ja"] as const;
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

export const DEFAULT_LANGUAGE: SupportedLanguage = "zh-Hans";
const LANGUAGE_STORAGE_KEY = "i18nextLng";

const resources = {
	"zh-Hans": zhHans,
	"zh-Hant": zhHant,
	en,
	ja,
};

const toHtmlLanguage = (language: SupportedLanguage): string => {
	switch (language) {
		case "zh-Hant":
			return "zh-TW";
		case "en":
			return "en";
		case "ja":
			return "ja";
		case "zh-Hans":
		default:
			return "zh-CN";
	}
};

export const normalizeLanguage = (value: string | null | undefined): SupportedLanguage => {
	const normalized = value?.trim().toLowerCase() ?? "";
	if (normalized === "zh-hant" || normalized.startsWith("zh-tw") || normalized.startsWith("zh-hk")) {
		return "zh-Hant";
	}
	if (normalized === "en" || normalized.startsWith("en-")) {
		return "en";
	}
	if (normalized === "ja" || normalized.startsWith("ja-")) {
		return "ja";
	}
	if (normalized === "zh-hans" || normalized.startsWith("zh-cn") || normalized.startsWith("zh-sg")) {
		return "zh-Hans";
	}
	return DEFAULT_LANGUAGE;
};

const readStoredLanguage = (): SupportedLanguage | null => {
	if (typeof window === "undefined") {
		return null;
	}
	try {
		const stored = window.localStorage.getItem(LANGUAGE_STORAGE_KEY);
		return stored ? normalizeLanguage(stored) : null;
	} catch {
		return null;
	}
};

const detectClientLanguage = (): SupportedLanguage => {
	const stored = readStoredLanguage();
	if (stored) {
		return stored;
	}
	if (typeof navigator !== "undefined") {
		return normalizeLanguage(navigator.language);
	}
	return DEFAULT_LANGUAGE;
};

const persistLanguage = (language: SupportedLanguage) => {
	if (typeof window !== "undefined") {
		try {
			window.localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
		} catch {
			// noop
		}
	}

	if (typeof document !== "undefined") {
		document.documentElement.lang = toHtmlLanguage(language);
	}
};

i18n.use(initReactI18next).init({
	resources,
	ns: ["language", "sidebar", "auth", "app", "tab", "scores", "settings", "adminStatic", "adminUsers", "aliases", "imports"],
	defaultNS: "app",
	lng: DEFAULT_LANGUAGE,
	fallbackLng: DEFAULT_LANGUAGE,
	supportedLngs: [...SUPPORTED_LANGUAGES],
	interpolation: {
		escapeValue: false,
	},
});

persistLanguage(DEFAULT_LANGUAGE);

export const syncClientLanguagePreference = () => {
	const nextLanguage = detectClientLanguage();
	persistLanguage(nextLanguage);
	if (i18n.resolvedLanguage !== nextLanguage) {
		void i18n.changeLanguage(nextLanguage);
	}
};

export const changeDashboardLanguage = async (language: string) => {
	const nextLanguage = normalizeLanguage(language);
	persistLanguage(nextLanguage);
	if (i18n.resolvedLanguage === nextLanguage) {
		return;
	}
	await i18n.changeLanguage(nextLanguage);
};

export default i18n;
