package com.aiyu.rewire.data

import com.aiyu.rewire.data.local.Converters
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val c = Converters()

    @Test fun metadataRoundTrips() {
        val m = mapOf("focus_minutes" to "25", "note" to "a \"quoted\" value")
        assertEquals(m, c.toMetadata(c.fromMetadata(m)))
        assertEquals(emptyMap<String, String>(), c.toMetadata(c.fromMetadata(emptyMap())))
    }

    @Test fun corruptMetadataReadsAsEmpty() = assertEquals(emptyMap<String, String>(), c.toMetadata("not json"))
}
