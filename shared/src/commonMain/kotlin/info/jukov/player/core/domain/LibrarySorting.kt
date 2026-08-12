package info.jukov.player.core.domain

import com.russhwolf.settings.Settings
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.download.domain.OfflineAlbum
import info.jukov.player.feature.download.domain.OfflineTrack
import info.jukov.player.feature.track.domain.Track

enum class SortDirection { Ascending, Descending }
enum class TrackSortCriterion { Title, Artist }
enum class AlbumSortCriterion { Title, Artist, Year }
enum class ArtistSortCriterion { Name }
enum class DownloadTrackSortCriterion { Title, Artist, Added }
enum class DownloadAlbumSortCriterion { Title, Artist, Year, Added }

data class SortOption<C>(val criterion: C, val direction: SortDirection)

interface SortPreferences {
    var artists: SortOption<ArtistSortCriterion>
    var albums: SortOption<AlbumSortCriterion>
    var artistTracks: SortOption<TrackSortCriterion>
    var downloadTracks: SortOption<DownloadTrackSortCriterion>
    var downloadAlbums: SortOption<DownloadAlbumSortCriterion>
}

class SettingsSortPreferences(private val settings: Settings = Settings()) : SortPreferences {
    override var artists: SortOption<ArtistSortCriterion>
        get() = read("sort.artists", ArtistSortCriterion.Name, SortDirection.Ascending)
        set(value) = write("sort.artists", value)
    override var albums: SortOption<AlbumSortCriterion>
        get() = read("sort.albums", AlbumSortCriterion.Title, SortDirection.Ascending)
        set(value) = write("sort.albums", value)
    override var artistTracks: SortOption<TrackSortCriterion>
        get() = read("sort.artistTracks", TrackSortCriterion.Title, SortDirection.Ascending)
        set(value) = write("sort.artistTracks", value)
    override var downloadTracks: SortOption<DownloadTrackSortCriterion>
        get() = read("sort.downloadTracks", DownloadTrackSortCriterion.Added, SortDirection.Descending)
        set(value) = write("sort.downloadTracks", value)
    override var downloadAlbums: SortOption<DownloadAlbumSortCriterion>
        get() = read("sort.downloadAlbums", DownloadAlbumSortCriterion.Added, SortDirection.Descending)
        set(value) = write("sort.downloadAlbums", value)

    private inline fun <reified C : Enum<C>> read(key: String, criterion: C, direction: SortDirection): SortOption<C> {
        val parts = settings.getStringOrNull(key)?.split(':')
        val savedCriterion = parts?.getOrNull(0)?.let { value -> enumValues<C>().firstOrNull { it.name == value } }
        val savedDirection = parts?.getOrNull(1)?.let { value -> SortDirection.entries.firstOrNull { it.name == value } }
        return SortOption(savedCriterion ?: criterion, savedDirection ?: direction)
    }

    private fun <C : Enum<C>> write(key: String, option: SortOption<C>) {
        settings.putString(key, "${option.criterion.name}:${option.direction.name}")
    }
}

private fun <T> List<T>.sorted(option: SortOption<*>, comparator: Comparator<T>): List<T> =
    sortedWith(if (option.direction == SortDirection.Ascending) comparator else comparator.reversed())

fun List<Artist>.sortedArtists(option: SortOption<ArtistSortCriterion>): List<Artist> =
    sorted(option, compareBy<Artist>({ it.name.lowercase() }, Artist::name, Artist::id))

fun List<Track>.sortedTracks(option: SortOption<TrackSortCriterion>): List<Track> {
    val comparator = when (option.criterion) {
        TrackSortCriterion.Title -> compareBy<Track>({ it.title.lowercase() }, Track::title, { it.artist.lowercase() }, Track::id)
        TrackSortCriterion.Artist -> compareBy<Track>({ it.artist.lowercase() }, Track::artist, { it.title.lowercase() }, Track::id)
    }
    return sorted(option, comparator)
}

fun List<Album>.sortedAlbums(option: SortOption<AlbumSortCriterion>): List<Album> {
    val comparator = when (option.criterion) {
        AlbumSortCriterion.Title -> compareBy<Album>({ it.name.lowercase() }, Album::name, { it.artist.lowercase() }, Album::id)
        AlbumSortCriterion.Artist -> compareBy<Album>({ it.artist.lowercase() }, Album::artist, { it.name.lowercase() }, Album::id)
        AlbumSortCriterion.Year -> compareBy<Album>({ it.year == null }, { it.year ?: 0 }, { it.name.lowercase() }, Album::id)
    }
    return sorted(option, comparator)
}

fun List<OfflineTrack>.sortedDownloadTracks(option: SortOption<DownloadTrackSortCriterion>): List<OfflineTrack> {
    val comparator = when (option.criterion) {
        DownloadTrackSortCriterion.Title -> compareBy<OfflineTrack>({ it.track.title.lowercase() }, { it.track.title }, { it.track.id })
        DownloadTrackSortCriterion.Artist -> compareBy<OfflineTrack>({ it.track.artist.lowercase() }, { it.track.artist }, { it.track.title.lowercase() }, { it.track.id })
        DownloadTrackSortCriterion.Added -> compareBy<OfflineTrack>({ it.requestedAtMs }, { it.track.id })
    }
    return sorted(option, comparator)
}

fun List<OfflineAlbum>.sortedDownloadAlbums(option: SortOption<DownloadAlbumSortCriterion>): List<OfflineAlbum> {
    val comparator = when (option.criterion) {
        DownloadAlbumSortCriterion.Title -> compareBy<OfflineAlbum>({ it.album.name.lowercase() }, { it.album.name }, { it.album.id })
        DownloadAlbumSortCriterion.Artist -> compareBy<OfflineAlbum>({ it.album.artist.lowercase() }, { it.album.artist }, { it.album.name.lowercase() }, { it.album.id })
        DownloadAlbumSortCriterion.Year -> compareBy<OfflineAlbum>({ it.album.year == null }, { it.album.year ?: 0 }, { it.album.name.lowercase() }, { it.album.id })
        DownloadAlbumSortCriterion.Added -> compareBy<OfflineAlbum>({ it.requestedAtMs }, { it.album.id })
    }
    return sorted(option, comparator)
}
