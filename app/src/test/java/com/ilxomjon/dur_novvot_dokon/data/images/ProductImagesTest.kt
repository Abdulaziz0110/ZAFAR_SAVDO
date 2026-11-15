package com.ilxomjon.dur_novvot_dokon.data.images

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductImagesTest {
    @Test fun fileNameRoundTrip() {
        for (id in listOf("1042", "A-7", "00015", "tovar 5/2", "Қанд", "a_b", "x.y", "")) {
            assertEquals(id, ProductImages.unsafeName(ProductImages.safeName(id)))
        }
    }

    @Test fun fileNameIsSafe() {
        val n = ProductImages.safeName("../etc/pass wd")
        assert(n.all { it.isLetterOrDigit() || it == '_' || it == '-' }) { n }
        assertEquals("1042", ProductImages.safeName("1042"))
    }

    @Test fun sampleKeepsEnoughPixels() {
        assertEquals(1, ProductImages.sampleFor(800, 600, 900))
        assertEquals(1, ProductImages.sampleFor(1700, 900, 900))
        assertEquals(2, ProductImages.sampleFor(1800, 900, 900))
        assertEquals(4, ProductImages.sampleFor(4000, 3000, 900))
    }
}
