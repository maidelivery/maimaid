package net.krtl.maimaid.ui.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import net.krtl.maimaid.R

enum class AppLanguageOption(
    val languageTag: String,
    @param:StringRes val labelRes: Int
) {
    SYSTEM("", R.string.language_system),
    ZH_HANS("zh-Hans", R.string.language_zh_hans),
    ZH_HANT("zh-Hant", R.string.language_zh_hant),
    ENGLISH("en", R.string.language_en),
    JAPANESE("ja", R.string.language_ja);

    companion object {
        fun current(): AppLanguageOption {
            val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            return entries.firstOrNull { it.languageTag == currentTag } ?: SYSTEM
        }

        fun apply(option: AppLanguageOption) {
            val locales = if (option == SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(option.languageTag)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
