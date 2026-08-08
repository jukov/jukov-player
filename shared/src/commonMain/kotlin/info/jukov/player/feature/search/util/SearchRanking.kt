package info.jukov.player.feature.search.util

import info.jukov.player.feature.search.domain.LibrarySearchItem

internal fun rankSearchItems(
    query: String,
    items: List<LibrarySearchItem>,
): List<LibrarySearchItem> = items.sortedBy { item -> searchRank(query, item) }

private data class SearchRank(
    val matchKind: Int,
    val matchField: Int,
    val matchDetail: Double,
    val itemType: Int,
    val stableId: String,
) : Comparable<SearchRank> {
    override fun compareTo(other: SearchRank): Int =
        compareValuesBy(
            this,
            other,
            SearchRank::itemType,
            SearchRank::matchKind,
            SearchRank::matchField,
            SearchRank::matchDetail,
            SearchRank::stableId,
        )
}

private data class TextMatch(
    val kind: Int,
    val fieldPriority: Int,
    val detail: Double,
) : Comparable<TextMatch> {
    override fun compareTo(other: TextMatch): Int =
        compareValuesBy(this, other, TextMatch::kind, TextMatch::fieldPriority, TextMatch::detail)
}

private fun searchRank(query: String, item: LibrarySearchItem): SearchRank {
    val match = item.searchableValues()
        .filter(String::isNotBlank)
        .mapIndexed { fieldPriority, value -> textMatch(query, value, fieldPriority) }
        .minOrNull()
        ?: TextMatch(Int.MAX_VALUE, Int.MAX_VALUE, Double.MAX_VALUE)
    return SearchRank(
        matchKind = match.kind,
        matchField = match.fieldPriority,
        matchDetail = match.detail,
        itemType = item.typePriority(),
        stableId = item.id,
    )
}

private fun textMatch(query: String, value: String, fieldPriority: Int): TextMatch {
    val needle = query.trim().lowercase()
    val text = value.trim().lowercase()
    if (text == needle) {
        return TextMatch(kind = 0, fieldPriority = fieldPriority, detail = 0.0)
    }
    if (text.startsWith(needle)) {
        return TextMatch(kind = 1, fieldPriority = fieldPriority, detail = (text.length - needle.length).toDouble())
    }
    val wordIndex = text.split(Regex("\\s+")).indexOfFirst { word -> word.startsWith(needle) }
    if (wordIndex >= 0) {
        return TextMatch(kind = 2, fieldPriority = fieldPriority, detail = wordIndex.toDouble())
    }
    val substringIndex = text.indexOf(needle)
    if (substringIndex >= 0) {
        return TextMatch(kind = 3, fieldPriority = fieldPriority, detail = substringIndex.toDouble())
    }
    val distance = levenshteinDistance(needle, text).toDouble() /
        maxOf(needle.length, text.length, 1)
    return TextMatch(kind = 4, fieldPriority = fieldPriority, detail = distance)
}

private fun LibrarySearchItem.typePriority(): Int = when (this) {
    is LibrarySearchItem.ArtistItem -> 0
    is LibrarySearchItem.AlbumItem -> 1
    is LibrarySearchItem.TrackItem -> 2
}

private fun LibrarySearchItem.searchableValues(): List<String> = when (this) {
    is LibrarySearchItem.ArtistItem -> listOf(artist.name)
    is LibrarySearchItem.AlbumItem -> listOf(album.name, album.artist)
    is LibrarySearchItem.TrackItem -> listOf(track.title, track.artist, track.album.orEmpty())
}

internal fun levenshteinDistance(left: String, right: String): Int {
    var previous = IntArray(right.length + 1) { index -> index }
    left.forEachIndexed { leftIndex, leftChar ->
        val current = IntArray(right.length + 1)
        current[0] = leftIndex + 1
        right.forEachIndexed { rightIndex, rightChar ->
            current[rightIndex + 1] = minOf(
                current[rightIndex] + 1,
                previous[rightIndex + 1] + 1,
                previous[rightIndex] + if (leftChar == rightChar) 0 else 1,
            )
        }
        previous = current
    }
    return previous[right.length]
}
