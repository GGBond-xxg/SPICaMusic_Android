package me.spica27.spicamusic.common.entity

data class GlassSettings(
    val smoothMode: Boolean = true,
    val blur: Int = 4,
    val refraction: Int = 25,
    val opacity: Int = 65,
) {
    fun encode(): String = "$smoothMode|$blur|$refraction|$opacity"

    companion object {
        fun decode(value: String): GlassSettings {
            val parts = value.split('|')
            return GlassSettings(
                smoothMode = parts.getOrNull(0)?.toBooleanStrictOrNull() ?: true,
                blur = (parts.getOrNull(1)?.toIntOrNull() ?: 4).coerceIn(0, 16),
                refraction = (parts.getOrNull(2)?.toIntOrNull() ?: 25).coerceIn(0, 100),
                opacity = (parts.getOrNull(3)?.toIntOrNull() ?: 65).coerceIn(20, 95),
            )
        }
    }
}
