package com.fingertipfuture.hub.core

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import com.fingertipfuture.hub.data.LanguageStore

/** 系统 Toast / 通知等不经过 Compose LocalizedText 的界面文字统一从这里转换。 */
internal fun Context.localizedUiText(text: String): String = LanguageStore.localize(text)

internal fun Context.localizedUiString(
    @StringRes resId: Int,
    vararg formatArgs: Any,
): String = localizedUiText(getString(resId, *formatArgs))

internal fun Context.showLocalizedToast(
    text: CharSequence,
    duration: Int = Toast.LENGTH_SHORT,
) {
    Toast.makeText(this, localizedUiText(text.toString()), duration).show()
}

internal fun Context.showLocalizedToast(
    @StringRes resId: Int,
    duration: Int = Toast.LENGTH_SHORT,
    vararg formatArgs: Any,
) {
    Toast.makeText(this, localizedUiString(resId, *formatArgs), duration).show()
}
