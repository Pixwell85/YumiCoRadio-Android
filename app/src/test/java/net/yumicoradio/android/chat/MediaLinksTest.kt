// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaLinksTest {

    @Test
    fun `plain text has no links`() {
        assertTrue(MediaLinks.find("just talking here").isEmpty())
    }

    @Test
    fun `classifies by extension the way the site does`() {
        val text = "pic https://s1.yumicoradio.net/chat/uploads/a.png " +
            "song https://s1.yumicoradio.net/chat/uploads/b.mp3 " +
            "clip https://s1.yumicoradio.net/chat/uploads/c.mp4 " +
            "doc https://s1.yumicoradio.net/chat/uploads/d.pdf"
        val kinds = MediaLinks.find(text).map { it.kind }
        assertEquals(
            listOf(
                MediaLinks.Kind.IMAGE,
                MediaLinks.Kind.AUDIO,
                MediaLinks.Kind.VIDEO,
                MediaLinks.Kind.FILE,
            ),
            kinds,
        )
    }

    @Test
    fun `a query string does not hide the extension`() {
        val link = MediaLinks.find("https://example.com/cat.jpg?size=large").single()
        assertEquals(MediaLinks.Kind.IMAGE, link.kind)
    }

    @Test
    fun `extension matching ignores case`() {
        assertEquals(MediaLinks.Kind.IMAGE, MediaLinks.find("https://x.test/A.PNG").single().kind)
    }

    @Test
    fun `an ordinary link is a plain link`() {
        val link = MediaLinks.find("see https://yumicoradio.net for details").single()
        assertEquals(MediaLinks.Kind.LINK, link.kind)
        assertEquals("https://yumicoradio.net", link.url)
    }

    @Test
    fun `trailing punctuation is not swallowed into the url`() {
        assertEquals(
            "https://yumicoradio.net/a.png",
            MediaLinks.find("look at https://yumicoradio.net/a.png.").single().url,
        )
        assertEquals(
            "https://yumicoradio.net",
            MediaLinks.find("(see https://yumicoradio.net)").single().url,
        )
    }

    @Test
    fun `several links in one message are all found in order`() {
        val urls = MediaLinks.find("https://a.test/1.png and https://b.test/2.mp3").map { it.url }
        assertEquals(listOf("https://a.test/1.png", "https://b.test/2.mp3"), urls)
    }

    @Test
    fun `uploads from the chat server are recognised as such`() {
        val ours = MediaLinks.find("https://s1.yumicoradio.net/chat/uploads/x.png").single()
        val theirs = MediaLinks.find("https://example.com/x.png").single()
        assertTrue(ours.isUpload, "an upload URL was not recognised")
        assertTrue(!theirs.isUpload, "a foreign URL was treated as an upload")
    }

    @Test
    fun `http and https are both matched`() {
        assertEquals(2, MediaLinks.find("http://a.test/x.png https://b.test/y.png").size)
    }

    @Test
    fun `recognises each platform by url the way the site does`() {
        fun p(url: String) = MediaLinks.find(url).single().platform
        assertEquals(MediaLinks.Platform.YOUTUBE, p("https://www.youtube.com/watch?v=abc"))
        assertEquals(MediaLinks.Platform.YOUTUBE, p("https://youtu.be/abc"))
        assertEquals(MediaLinks.Platform.SOUNDCLOUD, p("https://soundcloud.com/artist/track"))
        assertEquals(MediaLinks.Platform.SPOTIFY, p("https://open.spotify.com/track/xyz"))
        assertEquals(MediaLinks.Platform.BANDCAMP, p("https://name.bandcamp.com/track/song"))
        assertEquals(MediaLinks.Platform.BANDCAMP, p("https://name.bandcamp.com/album/rec"))
        assertEquals(MediaLinks.Platform.VIMEO, p("https://vimeo.com/12345"))
        assertEquals(MediaLinks.Platform.DAILYMOTION, p("https://www.dailymotion.com/video/x9"))
        assertEquals(MediaLinks.Platform.DAILYMOTION, p("https://dai.ly/x9"))
        assertEquals(MediaLinks.Platform.TWITCH, p("https://twitch.tv/streamer"))
        assertEquals(MediaLinks.Platform.STREAMABLE, p("https://streamable.com/abcd"))
        assertEquals(MediaLinks.Platform.IMGUR, p("https://imgur.com/gallery/abc"))
        assertEquals(MediaLinks.Platform.GYAZO, p("https://gyazo.com/abc123"))
    }

    @Test
    fun `direct image hosts are not treated as page embeds`() {
        // i.imgur / i.gyazo are direct images (kind IMAGE), never a platform badge.
        assertEquals(null, MediaLinks.find("https://i.imgur.com/abc.png").single().platform)
        assertEquals(null, MediaLinks.find("https://i.gyazo.com/abc.png").single().platform)
    }

    @Test
    fun `an ordinary link and a media url have no platform`() {
        assertEquals(null, MediaLinks.find("https://yumicoradio.net").single().platform)
        assertEquals(null, MediaLinks.find("https://example.com/cat.jpg").single().platform)
    }

    @Test
    fun `trailing punctuation is trimmed before platform matching`() {
        assertEquals(
            MediaLinks.Platform.YOUTUBE,
            MediaLinks.find("watch (https://youtu.be/abc)").single().platform,
        )
    }

    @Test
    fun `spans locate the url inside the text`() {
        val text = "see https://yumicoradio.net now"
        val span = MediaLinks.spans(text).single()
        assertEquals("https://yumicoradio.net", span.url)
        assertEquals(text.substring(span.start, span.end), "https://yumicoradio.net")
    }

    @Test
    fun `spans exclude trailing punctuation`() {
        val text = "look (https://yumicoradio.net)"
        val span = MediaLinks.spans(text).single()
        assertEquals("https://yumicoradio.net", span.url)
        assertEquals(text.substring(span.start, span.end), "https://yumicoradio.net")
    }

    @Test
    fun `spans finds each url in order`() {
        val spans = MediaLinks.spans("a https://x.test/1 b https://y.test/2 c")
        assertEquals(listOf("https://x.test/1", "https://y.test/2"), spans.map { it.url })
    }

    @Test
    fun `no urls means no spans`() {
        assertTrue(MediaLinks.spans("just talking").isEmpty())
    }
}
