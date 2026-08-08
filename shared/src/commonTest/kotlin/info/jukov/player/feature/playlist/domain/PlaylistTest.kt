package info.jukov.player.feature.playlist.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistTest {
    @Test
    fun editablePlaylistAllowsItsOwner() {
        assertTrue(Playlist(id = "1", name = "Mine", owner = "Alice").isEditableBy("alice"))
    }

    @Test
    fun foreignAndReadOnlyPlaylistsAreNotEditable() {
        assertFalse(Playlist(id = "1", name = "Foreign", owner = "Bob").isEditableBy("Alice"))
        assertFalse(Playlist(id = "2", name = "Locked", owner = "Alice", readOnly = true).isEditableBy("Alice"))
    }

    @Test
    fun missingOwnerFallsBackToEditableForOlderServers() {
        assertTrue(Playlist(id = "1", name = "Legacy").isEditableBy("Alice"))
    }
}
