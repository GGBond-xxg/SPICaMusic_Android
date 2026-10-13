package me.spica27.spicamusic.cloud

import androidx.annotation.StringRes
import me.spica27.spicamusic.App
import me.spica27.spicamusic.AppLocaleController

internal fun cloudText(
    @StringRes id: Int,
    vararg args: Any,
): String = AppLocaleController.wrap(App.getInstance()).getString(id, *args)
