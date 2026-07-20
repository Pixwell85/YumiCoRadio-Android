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
}
