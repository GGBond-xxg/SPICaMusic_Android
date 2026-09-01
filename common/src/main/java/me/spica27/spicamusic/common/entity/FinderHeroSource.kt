package me.spica27.spicamusic.common.entity

import androidx.compose.runtime.Immutable

/** 发现页首张推荐卡的数据来源。 */
@Immutable
enum class FinderHeroSource(
    val value: String,
) {
    RECENT_FREQUENT("recent_frequent"),
    NETEASE_DAILY("netease_daily"),
    ;

    companion object {
        fun fromString(value: String): FinderHeroSource =
            entries.firstOrNull { it.value == value } ?: RECENT_FREQUENT
    }
}
