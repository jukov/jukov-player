package info.jukov.player.feature.favorite.domain

import info.jukov.player.feature.track.domain.Track

fun favoriteStateForSelection(tracks: List<Track>): Boolean =
    tracks.any { !it.isFavorite }
