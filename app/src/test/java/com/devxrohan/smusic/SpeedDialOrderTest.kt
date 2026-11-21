package com.devxrohan.smusic

import com.devxrohan.smusic.customization.SpeedDialOrder
import org.junit.Assert.*
import org.junit.Test

class SpeedDialOrderTest {
    @Test fun newPinsAppendAcrossSongAndCollectionBoundaries() {
        val firstPage = (1..8).map { "song:$it" }
        val order = SpeedDialOrder.toggle(firstPage, "artist/next")
        assertEquals(firstPage, order.take(8))
        assertEquals("artist/next", order.last())
        assertEquals(order, SpeedDialOrder.reconcile(order, order.reversed()))
    }
    @Test fun removalAndReadditionMoveOnlyThatPinToTheEnd() {
        val original = listOf("song:a", "album/b", "song:c")
        val removed = SpeedDialOrder.toggle(original, "album/b")
        assertEquals(listOf("song:a", "song:c", "album/b"), SpeedDialOrder.toggle(removed, "album/b"))
    }
    @Test fun legacyAndRestoredMembershipMigrateWithoutDuplicatesOrStalePins() {
        assertEquals(listOf("a", "b"), SpeedDialOrder.reconcile(emptyList(), listOf("a", "b")))
        assertEquals(listOf("b", "a", "c"), SpeedDialOrder.reconcile(listOf("b", "gone", "b"), listOf("a", "b", "c")))
        val order = listOf("song:a", "artist/x")
        assertEquals(order, SpeedDialOrder.decode(SpeedDialOrder.encode(order)))
        assertEquals(emptyList<String>(), SpeedDialOrder.decode("bad"))
    }
}
