package com.beatarena.app

enum class DeckSide {
    A,
    B,
}

object MixMath {
    fun outputGain(
        side: DeckSide,
        master: Float,
        channel: Float,
        crossfader: Float,
    ): Float {
        val masterLevel = master.coerceIn(0f, 1f)
        val channelLevel = channel.coerceIn(0f, 1f)
        val crossfadeLevel = crossfader.coerceIn(0f, 1f)
        val deckLevel = when (side) {
            DeckSide.A -> 1f - crossfadeLevel
            DeckSide.B -> crossfadeLevel
        }
        return (masterLevel * channelLevel * deckLevel).coerceIn(0f, 1f)
    }
}
