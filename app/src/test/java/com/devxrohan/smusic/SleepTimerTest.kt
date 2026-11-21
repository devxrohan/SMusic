package com.devxrohan.smusic

import androidx.media3.common.Player
import com.devxrohan.smusic.playback.SleepTimer
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test

class SleepTimerTest {
    @Test fun fullTrackSliderRangeStopsAfterExactlyOneHundredCompletions() = withTimer { timer, pauses ->
        timer.startTrackCount(100)
        assertThrows(IllegalArgumentException::class.java) { timer.startTrackCount(101) }
        assertThrows(IllegalArgumentException::class.java) { timer.startTrackCount(0) }
        assertEquals(100, timer.tracksRemaining)
        repeat(99) { timer.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) }
        assertEquals(1, timer.tracksRemaining)
        assertEquals(0, pauses())
        timer.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        assertEquals(1, pauses())
        assertFalse(timer.isActive)
    }

    private fun withTimer(block: (SleepTimer, () -> Int) -> Unit) {
        var pauses = 0
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            if (method.name == "pause") pauses++
            null
        } as Player
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try { block(SleepTimer(scope, player)) { pauses } } finally { scope.cancel() }
    }

    @Test fun switchingFromEndOfSongToMinutesDoesNotPauseOnNextTrack() = withTimer { timer, pauses ->
        timer.start(-1)
        timer.start(30)
        assertFalse(timer.pauseWhenSongEnd)
        assertTrue(timer.triggerTime > System.currentTimeMillis())
        timer.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        assertEquals(0, pauses())
        timer.clear()
        assertFalse(timer.isActive)
    }

    @Test fun switchingToEndOfSongClearsCountdownAndFiresOnce() = withTimer { timer, pauses ->
        timer.start(30)
        timer.start(-1)
        assertEquals(-1L, timer.triggerTime)
        assertTrue(timer.pauseWhenSongEnd)
        timer.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        timer.onPlaybackStateChanged(Player.STATE_ENDED)
        assertEquals(1, pauses())
        assertFalse(timer.isActive)
    }

    @Test fun manualSeekDoesNotFinishEndOfSongTimerButRepeatDoes() = withTimer { timer, pauses ->
        timer.start(-1)
        timer.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        assertTrue(timer.isActive)
        assertEquals(0, pauses())
        timer.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        assertEquals(1, pauses())
        assertFalse(timer.isActive)
    }

    @Test fun finalTrackAndInvalidDurationsClearState() = withTimer { timer, pauses ->
        timer.start(-1)
        timer.onPlaybackStateChanged(Player.STATE_ENDED)
        assertEquals(1, pauses())
        assertFalse(timer.isActive)
        timer.start(30)
        timer.start(0)
        assertFalse(timer.isActive)
        timer.start(-2)
        assertFalse(timer.isActive)
    }
    @Test fun completedTrackCountIgnoresManualSkipsAndCountsRepeats() = withTimer { timer, pauses ->
        timer.startTrackCount(2)
        timer.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        assertEquals(2, timer.tracksRemaining)
        timer.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        assertEquals(1, timer.tracksRemaining); assertEquals(0, pauses())
        timer.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        assertFalse(timer.isActive); assertEquals(1, pauses())
    }
    @Test fun switchingTrackCountModesCancelsOldModeAndShortQueueClears() = withTimer { timer, pauses ->
        timer.start(30); timer.startTrackCount(5)
        assertEquals(-1L, timer.triggerTime); assertFalse(timer.pauseWhenSongEnd)
        timer.start(-1); assertEquals(0, timer.tracksRemaining)
        timer.startTrackCount(10); timer.onPlaybackStateChanged(Player.STATE_ENDED)
        assertFalse(timer.isActive); assertEquals(1, pauses())
    }
}
