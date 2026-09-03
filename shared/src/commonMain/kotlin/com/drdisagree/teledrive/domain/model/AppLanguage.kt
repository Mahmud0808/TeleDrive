package com.drdisagree.teledrive.domain.model

enum class AppLanguage(val code: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    RUSSIAN("ru");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            if (code == null) return SYSTEM
            return entries.firstOrNull {
                it.code.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true)
            } ?: SYSTEM
        }
    }
}
