package info.jukov.player.feature.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContentRangeTest {
    @Test
    fun parsesBoundedPartialResponse() {
        assertEquals(
            ParsedContentRange(start = 1_000, endInclusive = 1_999, total = 5_000),
            parseContentRange("bytes 1000-1999/5000"),
        )
    }

    @Test
    fun rejectsUnknownOrInvalidTotal() {
        assertNull(parseContentRange("bytes 1000-1999/*"))
        assertNull(parseContentRange("bytes 2000-1999/5000"))
        assertNull(parseContentRange("items 0-9/10"))
    }
}
