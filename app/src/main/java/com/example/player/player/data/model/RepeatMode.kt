package com.example.player.data.model

enum class RepeatMode {
    OFF,
    ONE,
    ALL;

    fun next(): RepeatMode {
        return when (this) {
            OFF -> ALL
            ALL -> ONE
            ONE -> OFF
        }
    }

    val displayName: String
        get() = when (this) {
            OFF -> "顺序播放"
            ALL -> "列表循环"
            ONE -> "单曲循环"
        }

    val iconDescription: String
        get() = when (this) {
            OFF -> "顺序播放"
            ALL -> "列表循环"
            ONE -> "单曲循环"
        }
}
