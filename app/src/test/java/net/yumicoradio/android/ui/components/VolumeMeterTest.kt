package net.yumicoradio.android.ui.components

import net.yumicoradio.android.ui.components.VolumeMeter.Tier
import kotlin.test.Test
import kotlin.test.assertEquals

class VolumeMeterTest {
    @Test fun zero_lights_nothing() {
        assertEquals(0, VolumeMeter.litSegments(0f))
    }
    @Test fun full_lights_all_ten() {
        assertEquals(10, VolumeMeter.litSegments(1f))
    }
    @Test fun half_lights_five() {
        assertEquals(5, VolumeMeter.litSegments(0.5f))
    }
    @Test fun rounds_to_nearest_segment() {
        assertEquals(1, VolumeMeter.litSegments(0.05f))  // 0.5 → 1
        assertEquals(0, VolumeMeter.litSegments(0.04f))  // 0.4 → 0
    }
    @Test fun clamps_out_of_range() {
        assertEquals(10, VolumeMeter.litSegments(1.5f))
        assertEquals(0, VolumeMeter.litSegments(-1f))
    }
    @Test fun tiers_split_six_two_two() {
        assertEquals(listOf(0,1,2,3,4,5).map { VolumeMeter.tier(it) }, List(6) { Tier.GREEN })
        assertEquals(Tier.ORANGE, VolumeMeter.tier(6))
        assertEquals(Tier.ORANGE, VolumeMeter.tier(7))
        assertEquals(Tier.RED, VolumeMeter.tier(8))
        assertEquals(Tier.RED, VolumeMeter.tier(9))
    }
}
