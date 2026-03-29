package com.whisperlm.app.domain.usecase.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val filePath: String? = null
)

@Singleton
class PlaybackUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    fun play(filePath: String, seekToMs: Long = 0L) {
        val file = File(filePath)
        if (!file.exists()) return

        val player = exoPlayer ?: ExoPlayer.Builder(context).build().also { exoPlayer = it }
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.value = _state.value.copy(
                    isPlaying = player.isPlaying,
                    durationMs = player.duration.coerceAtLeast(0L)
                )
                if (playbackState == Player.STATE_ENDED) {
                    _state.value = _state.value.copy(isPlaying = false, positionMs = 0L)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }
        })

        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
        player.prepare()
        if (seekToMs > 0) player.seekTo(seekToMs)
        player.play()
        _state.value = PlaybackState(isPlaying = true, filePath = filePath)
    }

    fun pause() {
        exoPlayer?.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun resume() {
        exoPlayer?.play()
        _state.value = _state.value.copy(isPlaying = true)
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun stop() {
        exoPlayer?.stop()
        _state.value = PlaybackState()
    }

    fun getCurrentPositionMs(): Long = exoPlayer?.currentPosition ?: 0L

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        _state.value = PlaybackState()
    }
}
