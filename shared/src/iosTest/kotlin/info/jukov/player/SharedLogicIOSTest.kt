package info.jukov.player

import info.jukov.player.feature.download.safeComponent
import info.jukov.player.feature.download.isSafeRelativePath
import info.jukov.player.feature.download.iosTaskDescription
import info.jukov.player.feature.download.parseIosTaskDescription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class SharedLogicIOSTest {

    @Test
    fun offlinePathComponentsAreStableAndAccountScoped() {
        val first = safeComponent("https://music.example|listener")
        val repeated = safeComponent("https://music.example|listener")
        val other = safeComponent("https://music.example|other")

        assertEquals(first, repeated)
        assertNotEquals(first, other)
        assertEquals(32, first.length)
    }

    @Test
    fun offlinePathsCannotEscapeAccountDirectory() {
        assertTrue(isSafeRelativePath("tracks/song.audio"))
        assertFalse(isSafeRelativePath("../song.audio"))
        assertFalse(isSafeRelativePath("tracks/../../song.audio"))
        assertFalse(isSafeRelativePath("/tmp/song.audio"))
        assertFalse(isSafeRelativePath("tracks\\..\\song.audio"))
    }

    @Test
    fun backgroundTaskMetadataIsAccountScopedWithoutCredentials() {
        val accountKey = "https://music.example|listener|secret"
        val description = iosTaskDescription("track", accountKey, "folder:track-id")
        val metadata = parseIosTaskDescription(description)

        assertEquals("track", metadata?.kind)
        assertEquals(safeComponent(accountKey), metadata?.accountToken)
        assertEquals("folder:track-id", metadata?.id)
        assertFalse(description.contains("listener"))
        assertFalse(description.contains("secret"))
        assertNull(parseIosTaskDescription("track:missing-id"))
        assertNull(parseIosTaskDescription("unknown:token:id"))
    }
}
