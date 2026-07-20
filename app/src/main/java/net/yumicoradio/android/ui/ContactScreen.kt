package net.yumicoradio.android.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import net.yumicoradio.android.R
import net.yumicoradio.android.ui.components.Win98Fieldset
import net.yumicoradio.android.ui.components.sunkenDeep
import net.yumicoradio.android.ui.components.tappable
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98
import net.yumicoradio.android.ui.theme.Win98Type

/** The address the website's contact window shows (`index.html:325`). */
private const val EMAIL = "yumi@yumicoradio.net"

/**
 * The website's Contact window, minus its mail form.
 *
 * The site posts to its own `sendmail` backend. On a phone that would mean re-implementing a form,
 * a captcha and an endpoint to reach an address the reader can simply be handed: tapping the address
 * opens whatever mail client they already use, already signed in. Same destination, nothing to
 * maintain, and no message of theirs sitting in a queue of mine.
 *
 * Everything else — the profile, the three social accounts, the closing note — is the site's own
 * copy, so the two read alike.
 */
@Composable
fun ColumnScope.ContactContent() {
    val uris = LocalUriHandler.current
    val context = LocalContext.current

    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        Win98Fieldset("Yumi Co. Radio") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.radio_logo),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).sunkenDeep().padding(2.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Yumi Co. Radio",
                        fontFamily = W95FA, fontSize = Win98Type.Heading,
                        fontWeight = FontWeight.Bold, color = Win98.Ink,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "24/7 Future Funk & City Pop Station",
                        fontFamily = W95FA, fontSize = Win98Type.Body, color = Win98.Ink,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Win98Fieldset("Direct Contact") {
            ContactItem(
                icon = R.drawable.ic_win_contact,
                label = "Email Support",
                linkText = EMAIL,
                description = "For bugs, issues, feedback, or anything else!",
                onClick = { openMail(context) },
            )
        }

        Spacer(Modifier.height(10.dp))

        Win98Fieldset("Social Networks") {
            ContactItem(
                icon = R.drawable.ic_social_lastfm,
                label = "Last.FM Profile",
                linkText = "YumiCoRadio",
                description = "Track our music history and stats",
                onClick = { uris.openUri("https://www.last.fm/user/YumiCoRadio/") },
            )
            Spacer(Modifier.height(8.dp))
            ContactItem(
                icon = R.drawable.ic_social_x,
                label = "X (Twitter)",
                linkText = "@YumiCoRadio",
                description = "Follow for updates and announcements",
                onClick = { uris.openUri("https://twitter.com/YumiCoRadio") },
            )
            Spacer(Modifier.height(8.dp))
            ContactItem(
                icon = R.drawable.ic_social_bluesky,
                label = "BlueSky",
                linkText = "@yumicoradio.bsky.social",
                description = "Follow for updates and announcements",
                onClick = { uris.openUri("https://bsky.app/profile/yumicoradio.bsky.social") },
            )
        }

        Spacer(Modifier.height(10.dp))

        Win98Fieldset("Support & Feedback") {
            Text(
                "Enjoying Yumi Co. Radio?",
                fontFamily = W95FA, fontSize = Win98Type.Body, fontWeight = FontWeight.Bold,
                color = Win98.Ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "I'd love to hear from you! Share your thoughts, song requests, or technical " +
                    "feedback.",
                fontFamily = W95FA, fontSize = Win98Type.Body, color = Win98.Ink,
                lineHeight = Win98Type.BodyLineHeight,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "All messages are read and appreciated!",
                fontFamily = W95FA, fontSize = Win98Type.Body, color = Win98.Ink,
            )
        }

        Spacer(Modifier.height(10.dp))
    }
}

/**
 * Hands the address to whatever mail client the reader already uses.
 *
 * `ACTION_SENDTO` with a `mailto:` URI, not `UriHandler.openUri`: the latter issues `ACTION_VIEW`,
 * which fewer clients claim for `mailto:`, and it *throws* when nothing handles the intent — a
 * crash on the one tap this screen exists for. Devices with no mail client at all get told so.
 *
 * The subject is prefilled so a reply lands recognisably rather than as a bare "(no subject)".
 */
private fun openMail(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$EMAIL")
        putExtra(Intent.EXTRA_SUBJECT, "Yumi Co. Radio app")
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No mail app found. Write to $EMAIL", Toast.LENGTH_LONG).show()
    }
}

/** The site's `.contact-item`: icon, then label over link over description. */
@Composable
private fun ContactItem(
    icon: Int,
    label: String,
    linkText: String,
    description: String,
    onClick: () -> Unit,
) {
    Row {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontFamily = W95FA, fontSize = Win98Type.Body, fontWeight = FontWeight.Bold,
                color = Win98.Ink,
            )
            Text(
                linkText,
                fontFamily = W95FA, fontSize = Win98Type.Body,
                color = Win98.Link, textDecoration = TextDecoration.Underline,
                modifier = Modifier.tappable(onClick).padding(vertical = 3.dp),
            )
            Text(
                description,
                fontFamily = W95FA, fontSize = Win98Type.Small, color = Win98.InkDim,
                lineHeight = Win98Type.BodyLineHeight,
            )
        }
    }
}
