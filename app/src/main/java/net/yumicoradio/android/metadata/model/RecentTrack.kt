package net.yumicoradio.android.metadata.model

data class RecentTrack(
    val artist: String,
    val title: String,
    val imageUrl: String?,
    val uts: Long?,          // played_at, unix seconds
    // Carried for the schedule, which groups the hour by playlist rather than by track.
    val playlist: String? = null,
    val duration: Int = 0,
)
