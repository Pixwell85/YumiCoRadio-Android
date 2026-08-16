// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class BatteryReliabilityTest {

    @Test fun `maps known manufacturers case-insensitively`() {
        assertEquals(Oem.XIAOMI, oemFromManufacturer("Xiaomi"))
        assertEquals(Oem.XIAOMI, oemFromManufacturer("XIAOMI"))
        assertEquals(Oem.XIAOMI, oemFromManufacturer("Redmi"))
        assertEquals(Oem.SAMSUNG, oemFromManufacturer("samsung"))
        assertEquals(Oem.OPPO, oemFromManufacturer("OPPO"))
        assertEquals(Oem.VIVO, oemFromManufacturer("vivo"))
        assertEquals(Oem.ONEPLUS, oemFromManufacturer("OnePlus"))
        assertEquals(Oem.REALME, oemFromManufacturer("realme"))
        assertEquals(Oem.HUAWEI, oemFromManufacturer("HUAWEI"))
        assertEquals(Oem.HUAWEI, oemFromManufacturer("Honor"))
    }

    @Test fun `unknown or blank manufacturer is OTHER`() {
        assertEquals(Oem.OTHER, oemFromManufacturer("Google"))
        assertEquals(Oem.OTHER, oemFromManufacturer(""))
        assertEquals(Oem.OTHER, oemFromManufacturer("Fairphone"))
    }

    @Test fun `known OEMs carry guidance, OTHER does not`() {
        assertNotNull(oemGuidance(Oem.XIAOMI))
        assertNotNull(oemGuidance(Oem.SAMSUNG))
        assertNull(oemGuidance(Oem.OTHER))
    }

    @Test fun `guidance label and instruction are non-blank`() {
        val g = oemGuidance(Oem.XIAOMI)!!
        assert(g.label.isNotBlank())
        assert(g.instruction.isNotBlank())
    }

    @Test fun `Xiaomi guidance names both manual HyperOS settings`() {
        val guidance = oemGuidance(Oem.XIAOMI)!!
        assertEquals("Xiaomi / HyperOS settings", guidance.label)
        assert(guidance.instruction.contains("Background autostart"))
        assert(guidance.instruction.contains("No restrictions"))
    }

    @Test fun `every non-OTHER Oem has guidance`() {
        Oem.entries.filter { it != Oem.OTHER }.forEach { assertNotNull(oemGuidance(it)) }
    }

    @Test fun `battery exemption errors are unknown rather than allowed`() {
        assertEquals(BatteryExemption.UNKNOWN, batteryExemption(null))
    }

    @Test fun `battery exemption maps both real answers`() {
        assertEquals(BatteryExemption.ALLOWED, batteryExemption(true))
        assertEquals(BatteryExemption.RESTRICTED, batteryExemption(false))
    }

    @Test fun `protection summary never calls unknown battery state allowed`() {
        assertEquals(
            "Android battery exemption could not be verified.",
            batteryExemptionSummary(BatteryExemption.UNKNOWN),
        )
    }

    @Test fun `restricted apps request their own Doze exemption directly`() {
        assertEquals(
            BatterySettingsDestination.REQUEST_APP_EXEMPTION,
            batterySettingsDestination(BatteryExemption.RESTRICTED),
        )
        assertEquals(
            BatterySettingsDestination.OPTIMIZATION_LIST,
            batterySettingsDestination(BatteryExemption.ALLOWED),
        )
        assertEquals(
            BatterySettingsDestination.OPTIMIZATION_LIST,
            batterySettingsDestination(BatteryExemption.UNKNOWN),
        )
    }
}
