package net.yumicoradio.android.metadata

data class IcyTitle(val artist: String, val title: String)

object IcyParser {
    fun parse(streamTitle: String?): IcyTitle? {
        val s = streamTitle?.trim().orEmpty()
        if (s.isEmpty()) return null
        val dash = s.indexOf(" - ")
        return if (dash >= 0) {
            IcyTitle(s.substring(0, dash).trim(), s.substring(dash + 3).trim())
        } else {
            IcyTitle("", s)
        }
    }
}
