package com.focusguard.app.config

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Represents a single blocked section within an app.
 * @param label Human-readable name (e.g. "Reels")
 * @param keywords UI text keywords to match (case-insensitive)
 * @param isEnabled Whether this rule is active
 */
data class BlockedSection(
    val label: String,
    val keywords: List<String>,
    var isEnabled: Boolean = true
)

/**
 * Represents a monitored app with its blocked sections.
 */
data class BlockedAppConfig(
    val packageName: String,
    val appLabel: String,
    val sections: MutableList<BlockedSection>
)

/**
 * Master config registry for all blocked apps/sections.
 * Provides default rules for Instagram and extensible structure for other apps.
 */
object BlockedAppsRegistry {

    fun defaultConfig(): List<BlockedAppConfig> = listOf(
        BlockedAppConfig(
            packageName = "com.instagram.android",
            appLabel = "Instagram",
            sections = mutableListOf(
                BlockedSection(
                    label = "Reels",
                    keywords = listOf("reels", "reel"),
                    isEnabled = true
                ),
                BlockedSection(
                    label = "Explore",
                    keywords = listOf("explore", "search"),
                    isEnabled = true
                ),
                BlockedSection(
                    label = "Shop",
                    keywords = listOf("shop", "shopping"),
                    isEnabled = false
                )
            )
        ),
        BlockedAppConfig(
            packageName = "com.google.android.youtube",
            appLabel = "YouTube",
            sections = mutableListOf(
                BlockedSection(
                    label = "Shorts",
                    keywords = listOf("shorts"),
                    isEnabled = false
                )
            )
        )
    )

    fun toJson(configs: List<BlockedAppConfig>): String = Gson().toJson(configs)

    fun fromJson(json: String): List<BlockedAppConfig> {
        val type = object : TypeToken<List<BlockedAppConfig>>() {}.type
        return Gson().fromJson(json, type)
    }
}
