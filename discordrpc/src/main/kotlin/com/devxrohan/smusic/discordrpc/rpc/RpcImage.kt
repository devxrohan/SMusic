package com.devxrohan.smusic.discordrpc.rpc

import com.devxrohan.smusic.discordrpc.repository.DiscordRpcRepository

/**
 * Modified by Zion Huang
 */
sealed class RpcImage {
    abstract suspend fun resolveImage(repository: DiscordRpcRepository, applicationId: String? = null): String?

    class DiscordImage(val image: String) : RpcImage() {
        override suspend fun resolveImage(repository: DiscordRpcRepository, applicationId: String?): String {
            return "mp:${image}"
        }
    }

    class ExternalImage(val image: String) : RpcImage() {
        override suspend fun resolveImage(repository: DiscordRpcRepository, applicationId: String?): String? {
            return repository.getImage(image, applicationId)
        }
    }
}
