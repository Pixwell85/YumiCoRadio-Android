// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import net.yumicoradio.android.ui.theme.Win98Metrics

/**
 * A pop-up wearing the same frame as every other window in the app: a [Win98Window] floating over a
 * scrim, rather than a Material `AlertDialog`.
 *
 * The dialogs were the last surfaces still showing Material chrome — rounded corners, a tonal
 * surface, ripple on the buttons — which read as another program's dialog opening on top of this
 * one. `usePlatformDefaultWidth = false` is what lets the window size itself; without it Material
 * imposes its own width and the frame sits inside a second, invisible one.
 *
 * **Dismissability is deliberate, and [onDismiss] carries it.** A non-null lambda means every exit
 * works the same way: the title-bar X, the back gesture, and a tap on the scrim all call it. Passing
 * null makes the dialog modal in the strict sense — no X is drawn and neither back nor an outside
 * tap escapes — so pass null only for a pop-up the reader genuinely cannot be allowed to skip. A
 * prompt with no way out froze the whole app once (beta7); every caller today passes a real lambda.
 *
 * [buttons] is the Win9x button row: right-aligned along the bottom edge, which is where the idiom
 * puts OK and Cancel.
 */
@Composable
fun Win98Dialog(
    title: String,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = onDismiss != null,
            dismissOnClickOutside = onDismiss != null,
        ),
    ) {
        // A Dialog is its own window with its own soft-input mode — it does **not** inherit the
        // activity's `adjustResize`, so without this the window never shrinks for the keyboard and
        // `imePadding` has a zero inset to work with. The password field would then sit behind the
        // keyboard, unanswerable, on every launch a reserved nickname is used.
        AdjustResizeForKeyboard()
        Box(Modifier.fillMaxSize().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Win98Window(
                title = title,
                icon = icon,
                onClose = onDismiss,
                modifier = modifier.fillMaxWidth().widthIn(max = 380.dp),
            ) {
                content()
                if (buttons != null) {
                    Spacer(Modifier.height(Win98Metrics.ElementSpacing))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            Win98Metrics.GroupedButtonSpacing,
                            Alignment.End,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        content = buttons,
                    )
                }
            }
        }
    }
}

/**
 * Puts the enclosing dialog's own window into `adjustResize`.
 *
 * Reached through [DialogWindowProvider] rather than the activity: Compose gives a `Dialog` a real
 * platform window, and its soft-input mode starts unspecified regardless of what the manifest says
 * for the activity. Setting it here is what makes the window shrink for the keyboard, which is in
 * turn what gives `imePadding` a non-zero inset to lift the content by.
 */
@Composable
internal fun AdjustResizeForKeyboard() {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window
            ?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
