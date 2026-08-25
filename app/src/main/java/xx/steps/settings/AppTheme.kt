package xx.steps.settings

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import java.util.Locale

/** Light/dark override; SYSTEM follows the device setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * One entry of the language picker. [tag] is a BCP-47 tag, or empty for "follow the device".
 * [label] is the language's own name — a list of languages reads best in the languages themselves.
 */
data class LanguageOption(val tag: String, val label: String)

/**
 * The languages this build actually ships, read from `res/xml/locales_config.xml` through the
 * platform's [LocaleConfig]. Adding a `values-xx` folder and a line to that XML is then all it
 * takes for the picker to offer it — nothing here needs touching.
 *
 * [systemLabel] is the localized caption for the first entry, which hands the choice back to the
 * device setting.
 */
fun supportedLanguages(context: Context, systemLabel: String): List<LanguageOption> {
    val supported = LocaleConfig(context).supportedLocales
    val languages = buildList {
        add(LanguageOption(tag = "", label = systemLabel))
        for (index in 0 until (supported?.size() ?: 0)) {
            val locale = supported!![index]
            add(
                LanguageOption(
                    tag = locale.toLanguageTag(),
                    label = locale.getDisplayLanguage(locale)
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
                ),
            )
        }
    }
    // The config's order is the XML's; sort the real languages so the list does not depend on it.
    return languages.take(1) + languages.drop(1).sortedBy { it.label.lowercase(Locale.getDefault()) }
}

/**
 * Language uses the framework's per-app locales (API 33+, pure AOSP): the system persists the
 * choice and recreates the activity, so it is read from and written to the platform rather than
 * mirrored into [AppSettings]. An empty tag means "follow the device".
 */
fun currentLanguageTag(context: Context): String =
    context.getSystemService(LocaleManager::class.java)
        .applicationLocales
        .takeUnless { it.isEmpty }
        ?.get(0)
        ?.toLanguageTag()
        .orEmpty()

fun setLanguageTag(context: Context, tag: String) {
    context.getSystemService(LocaleManager::class.java).applicationLocales =
        if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
}
