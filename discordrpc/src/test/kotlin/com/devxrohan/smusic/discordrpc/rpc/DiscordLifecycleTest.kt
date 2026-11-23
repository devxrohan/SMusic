package com.devxrohan.smusic.discordrpc.rpc

import com.devxrohan.smusic.discordrpc.gateway.DiscordWebSocket
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test

class DiscordLifecycleTest {
    @Test fun connectionOwnsOneCancellableScope() {
        val socket = DiscordWebSocket("unused-test-token")
        val job = socket.coroutineContext[Job]
        assertSame(job, socket.coroutineContext[Job])
        assertNotNull(job)
        socket.close()
        assertTrue(job!!.isCancelled)
    }
}
