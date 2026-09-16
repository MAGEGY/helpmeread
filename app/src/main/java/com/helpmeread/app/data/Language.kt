package com.helpmeread.app.data

data class Language(
    val code: String,
    val name: String,
    val flag: String,
    val ttsLocaleCode: String = code
) {
    companion object {
        val supportedLanguages = listOf(
            Language("en", "English", "🇬🇧"),
            Language("ar", "العربية", "🇸🇦"),
            Language("fr", "Français", "🇫🇷"),
            Language("es", "Español", "🇪🇸"),
            Language("de", "Deutsch", "🇩🇪"),
            Language("hi", "हिन्दी", "🇮🇳"),
            Language("zh", "中文", "🇨🇳"),
            Language("ja", "日本語", "🇯🇵"),
            Language("ko", "한국어", "🇰🇷"),
            Language("ru", "Русский", "🇷🇺"),
            Language("it", "Italiano", "🇮🇹"),
            Language("pt", "Português", "🇧🇷"),
            Language("tr", "Türkçe", "🇹🇷"),
            Language("ur", "اردو", "🇵🇰"),
            Language("bn", "বাংলা", "🇧🇩")
        )
        
        val default = supportedLanguages[1] // Arabic as default
    }
}
