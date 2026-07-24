// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.packaging

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The APK must not carry an `org.json` implementation.
 *
 * socket.io-client declares that dependency, but Android ships those classes in android.jar, so
 * `app/build.gradle.kts` excludes it. Its JSON License ("shall be used for Good, not Evil") is
 * rejected by F-Droid and Debian, and this app ships GPLv3 on F-Droid — so a dropped exclusion is
 * a licensing regression, not a size regression.
 *
 * This reads each dex's `class_defs` table rather than grepping for the type name. The distinction
 * matters: a correct APK *references* `Lorg/json/JSONObject;` on every socket.io call and would
 * fail a naive byte search. Only a class actually **defined** in the dex means the implementation
 * was packaged.
 *
 * Skipped when no APK has been built: `testDebugUnitTest` does not depend on assembling one.
 */
class NoJsonLicenseTest {

    @Test
    fun `no org_json classes are packaged`() {
        val apks = findApks()
        assumeTrue(
            "no APK built yet (looked under ${File("build/outputs/apk").absolutePath}) — " +
                "run ./gradlew assembleDebug first",
            apks.isNotEmpty(),
        )

        apks.forEach { apk ->
            val offenders = definedClasses(apk).filter { it.startsWith("Lorg/json/") }
            if (offenders.isNotEmpty()) {
                fail(
                    "${apk.name} defines ${offenders.size} org.json class(es): " +
                        "${offenders.take(5)}. Restore the exclude in app/build.gradle.kts — the " +
                        "JSON License must not ship in an F-Droid GPLv3 release.",
                )
            }
        }
    }

    /** Guards the guard: if the dex parser read nothing, a green result would be meaningless. */
    @Test
    fun `the dex parser actually reads classes`() {
        val apks = findApks()
        assumeTrue("no APK built yet", apks.isNotEmpty())

        apks.forEach { apk ->
            val classes = definedClasses(apk)
            assertTrue(
                classes.size > 100,
                "only parsed ${classes.size} classes from ${apk.name} — the dex reader is broken",
            )
            assertTrue(
                classes.any { it.startsWith("Lnet/yumicoradio/android/") },
                "parsed no app classes from ${apk.name} — the dex reader is broken",
            )
        }
    }

    /**
     * Every APK present, not just the first found.
     *
     * Checking only one lets a stale artifact hide a live regression: a release APK left over from
     * an earlier build passed this test while the debug APK built seconds earlier was carrying 19
     * org.json classes.
     */
    private fun findApks(): List<File> = listOf(
        "build/outputs/apk/release/app-release.apk",
        "build/outputs/apk/debug/app-debug.apk",
    ).map { File(it) }.filter { it.isFile }

    private fun definedClasses(apk: File): List<String> = ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.endsWith(".dex") }
            .flatMap { entry -> dexClassNames(zip.getInputStream(entry).readBytes()).asSequence() }
            .toList()
    }

    /**
     * Minimal DEX reader: header → class_defs → type_ids → string_ids → the descriptor string.
     * Offsets are fixed by the DEX format; only the pieces needed to list defined classes are read.
     */
    private fun dexClassNames(dex: ByteArray): List<String> {
        val buf = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN)
        // Header offsets are fixed by the format: each section has a size field followed by its
        // offset field, so string_ids_off is 60 (not 56, which is its size) and type_ids_off is 68.
        val stringIdsOff = buf.getInt(60)
        val typeIdsOff = buf.getInt(68)
        val classDefsSize = buf.getInt(96)
        val classDefsOff = buf.getInt(100)

        return (0 until classDefsSize).map { i ->
            val typeIdx = buf.getInt(classDefsOff + i * CLASS_DEF_ITEM_SIZE)
            val descriptorIdx = buf.getInt(typeIdsOff + typeIdx * 4)
            val stringDataOff = buf.getInt(stringIdsOff + descriptorIdx * 4)
            readMutf8(dex, stringDataOff)
        }
    }

    /** String data is a ULEB128 length followed by MUTF-8 bytes; descriptors here are all ASCII. */
    private fun readMutf8(dex: ByteArray, offset: Int): String {
        var pos = offset
        var shift = 0
        var length = 0
        while (true) {
            val byte = dex[pos++].toInt()
            length = length or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
        }
        val end = (pos until dex.size).first { dex[it].toInt() == 0 }
        return String(dex, pos, end - pos, Charsets.UTF_8)
    }

    private companion object {
        const val CLASS_DEF_ITEM_SIZE = 32
    }
}
