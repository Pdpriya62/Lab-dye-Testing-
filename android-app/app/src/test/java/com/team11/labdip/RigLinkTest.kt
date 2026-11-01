package com.team11.labdip

import com.team11.labdip.data.model.RigLink
import com.team11.labdip.data.model.rigLink
import org.junit.Assert.assertEquals
import org.junit.Test

class RigLinkTest {
    @Test fun lastWillOfflineWinsEvenWithFreshData() = assertEquals(RigLink.Offline, rigLink(false, 1_000))

    @Test fun freshReadingIsOnline() {
        assertEquals(RigLink.Online, rigLink(null, 3_000))
        assertEquals(RigLink.Online, rigLink(true, 3_000))
    }

    @Test fun onlineButSilentIsQuiet() = assertEquals(RigLink.Quiet, rigLink(true, 60_000))

    @Test fun onlineStatusBeforeFirstReadingIsOnline() = assertEquals(RigLink.Online, rigLink(true, null))

    @Test fun noStatusAndNoFreshDataIsOffline() {
        assertEquals(RigLink.Offline, rigLink(null, null))
        assertEquals(RigLink.Offline, rigLink(null, 60_000))
    }
}
