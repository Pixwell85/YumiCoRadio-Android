// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class NotificationVoteIconTest {
    @Test
    fun `active vote icons remain distinct after notification tinting`() {
        assertTrue(drawableFile("ic_vote_heart_notification").isFile)
        assertTrue(drawableFile("ic_vote_heart_broken_notification").isFile)
        assertNotEquals(
            vectorSilhouette("ic_vote_heart_notification"),
            vectorSilhouette("ic_vote_heart_active"),
        )
        assertNotEquals(
            vectorSilhouette("ic_vote_heart_broken_notification"),
            vectorSilhouette("ic_vote_heart_broken_active"),
        )
    }

    private fun vectorSilhouette(name: String): List<VectorPathShape> {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(drawableFile(name))
        val paths = document.getElementsByTagName("path")
        return (0 until paths.length).map { index ->
            val attributes = paths.item(index).attributes
            VectorPathShape(
                pathData = attributes.androidValue("pathData") ?: error("Missing pathData in $name"),
                fillAlpha = attributes.androidValue("fillAlpha") ?: "1",
                strokeWidth = attributes.androidValue("strokeWidth") ?: "0",
            )
        }
    }

    private fun drawableFile(name: String): File {
        val drawableRoot = listOf(
            File("src/main/res/drawable"),
            File("app/src/main/res/drawable"),
        ).firstOrNull(File::isDirectory) ?: error("Android drawable directory not found")
        return File(drawableRoot, "$name.xml")
    }

    private fun org.w3c.dom.NamedNodeMap.androidValue(name: String): String? =
        getNamedItemNS("http://schemas.android.com/apk/res/android", name)?.nodeValue

    private data class VectorPathShape(
        val pathData: String,
        val fillAlpha: String,
        val strokeWidth: String,
    )
}
