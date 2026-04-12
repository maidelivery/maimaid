import { useTranslation } from "react-i18next";
import { GlobeIcon } from "lucide-react";
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Field, FieldLabel } from "@/components/ui/field";
import { changeDashboardLanguage, normalizeLanguage } from "@/lib/i18n";

const languages = [
	{ code: "zh-Hans", label: "简体中文" },
	{ code: "zh-Hant", label: "繁體中文" },
	{ code: "en", label: "English" },
	{ code: "ja", label: "日本語" },
];

export function LanguageSwitcher() {
	const { i18n, t } = useTranslation();

	const handleLanguageChange = (value: string) => {
		void changeDashboardLanguage(value);
	};

	const currentLanguage = normalizeLanguage(i18n.resolvedLanguage);

	return (
		<Field>
			<FieldLabel className="flex items-center gap-2">
				<GlobeIcon className="h-4 w-4" />
				{t("language:language", "Language")}
			</FieldLabel>
			<Select value={currentLanguage} onValueChange={handleLanguageChange}>
				<SelectTrigger className="w-full">
					<SelectValue placeholder="Select Language" />
				</SelectTrigger>
				<SelectContent>
					<SelectGroup>
						{languages.map((lang) => (
							<SelectItem key={lang.code} value={lang.code}>
								{lang.label}
							</SelectItem>
						))}
					</SelectGroup>
				</SelectContent>
			</Select>
		</Field>
	);
}
