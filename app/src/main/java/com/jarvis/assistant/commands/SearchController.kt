package com.jarvis.assistant.commands

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens Google / YouTube search results, and generic URIs, via standard intents.
 * Actual song lookup-and-autoplay lives in [SongPlaybackController] — these plain
 * "open a search screen" links can't start a specific track playing on their own.
 */
class SearchController(private val context: Context) {

    fun searchGoogle(query: String): String {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "\"$query\" için Google'da arama yapıyorum."
    }

    fun searchYoutube(query: String): String {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "\"$query\" için YouTube'da arama yapıyorum."
    }

    /** Opens any URI (e.g. a resolved "spotify:track:..." URI or a youtube watch link) directly. */
    fun openUri(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /** Just opens Spotify's search screen — fallback when no API credentials are set or lookup fails. */
    fun openSpotifySearch(query: String): String {
        val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            setPackage("com.spotify.music")
        }
        return try {
            context.startActivity(spotifyIntent)
            "\"$query\" Spotify'da açılıyor."
        } catch (e: ActivityNotFoundException) {
            searchYoutube(query) + " (Spotify kurulu değil, YouTube'a yönlendirdim.)"
        }
    }

    /** Finds and actually starts playing a song (Spotify by default). */
    suspend fun playSong(query: String): String = SongPlaybackController.playOnSpotify(context, query, this)
}
