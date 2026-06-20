package com.example.player.data.model

data class EqualizerPreset(
    val id: Int,
    val name: String,
    val bandLevels: IntArray = IntArray(5) { 0 },
    val isCustom: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EqualizerPreset) return false
        return id == other.id && name == other.name && bandLevels.contentEquals(other.bandLevels)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + name.hashCode()
        result = 31 * result + bandLevels.contentHashCode()
        return result
    }
}
