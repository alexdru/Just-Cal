package com.justcal.app.ocr

import org.junit.Assert.*
import org.junit.Test

class CtcDecoderTest {
    @Test fun blankSeparatesRepeatedCharactersAndSpaceHasItsOwnIndex() {
        val indices = listOf(1, 1, 0, 1, 2, 3, 0)
        val data = FloatArray(indices.size * 4)
        indices.forEachIndexed { step, index -> data[step * 4 + index] = 0.9f }
        val result = CtcDecoder.decode(data, indices.size, 4, listOf("Я", "a", " "))
        assertEquals("ЯЯa ", result.first)
        assertEquals(0.9f, result.second, 0.001f)
    }
    @Test(expected = IllegalArgumentException::class) fun dictionaryMismatchFailsExplicitly() {
        CtcDecoder.decode(FloatArray(4), 1, 4, listOf("A"))
    }
}
