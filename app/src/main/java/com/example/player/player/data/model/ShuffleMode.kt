package com.example.player.data.model

enum class ShuffleMode {
    OFF, ON;

    fun toggle(): ShuffleMode = if (this == OFF) ON else OFF

    val displayName: String
        get() = if (this == ON) "随机播放" else "顺序播放"
}
