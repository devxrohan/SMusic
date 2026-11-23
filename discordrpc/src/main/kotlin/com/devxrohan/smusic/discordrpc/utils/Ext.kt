package com.devxrohan.smusic.discordrpc.utils

import com.devxrohan.smusic.discordrpc.rpc.RpcImage

fun String.toRpcImage(): RpcImage? {
    return if (this.isBlank())
        null
    else if (this.startsWith("attachments"))
        RpcImage.DiscordImage(this)
    else
        RpcImage.ExternalImage(this)
}

