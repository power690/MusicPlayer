package com.example.player.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "play_history",
    foreignKeys = [
        ForeignKey(
            entity = MusicSong::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlayHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: Long,
    val playedAt: Long = System.currentTimeMillis(),
    val completedDuration: Long = 0L,
    val isCompleted: Boolean = false
)
