package com.beatarena.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MixMathTest {
    @Test
    fun crossfaderFavorsTheSelectedDeck() {
        assertEquals(1f, MixMath.outputGain(DeckSide.A, 1f, 1f, 0f), 0.001f)
        assertEquals(0f, MixMath.outputGain(DeckSide.B, 1f, 1f, 0f), 0.001f)
        assertEquals(0f, MixMath.outputGain(DeckSide.A, 1f, 1f, 1f), 0.001f)
        assertEquals(1f, MixMath.outputGain(DeckSide.B, 1f, 1f, 1f), 0.001f)
    }

    @Test
    fun masterAndChannelLevelsScaleOutputAndClampInputs() {
        assertEquals(0.25f, MixMath.outputGain(DeckSide.A, 0.5f, 0.5f, 0f), 0.001f)
        assertEquals(0f, MixMath.outputGain(DeckSide.B, 2f, 1f, -1f), 0.001f)
        assertEquals(1f, MixMath.outputGain(DeckSide.A, 2f, 2f, -1f), 0.001f)
    }
}
