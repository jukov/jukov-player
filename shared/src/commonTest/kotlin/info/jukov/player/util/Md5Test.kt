package info.jukov.player.util

import kotlin.test.Test
import kotlin.test.assertEquals

class Md5Test {
    @Test
    fun hashesOpenSubsonicAuthenticationExample() {
        assertEquals(
            expected = "26719a1196d2a940705a59634eb18eab",
            actual = md5("sesamec19b2d"),
        )
    }
}
