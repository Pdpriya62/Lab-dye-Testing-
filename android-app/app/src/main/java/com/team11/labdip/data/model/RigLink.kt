package com.team11.labdip.data.model

/** Is the ESP32 itself there, separate from the phone's link to the broker. */
enum class RigLink { Online, Quiet, Offline }

/** Readings older than this mean the rig has stopped sending (it publishes every 3 s). */
const val RIG_STALE_MS = 10_000L

/**
 * [rigSays] is the retained status the firmware keeps on `.../status` ("online", or "offline" from its
 * MQTT last will); null when nothing was received, e.g. older firmware. [ageMs] is null with no reading yet.
 * Online but silent (colour sensor missing) is [RigLink.Quiet]; anything else without fresh data is offline.
 */
fun rigLink(rigSays: Boolean?, ageMs: Long?): RigLink = when {
    rigSays == false -> RigLink.Offline
    ageMs != null && ageMs <= RIG_STALE_MS -> RigLink.Online
    rigSays == true -> if (ageMs == null) RigLink.Online else RigLink.Quiet
    else -> RigLink.Offline
}
