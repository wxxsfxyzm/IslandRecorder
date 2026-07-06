package com.island.recorder.domain.recording.model

import android.os.Parcelable
import androidx.annotation.StringRes
import com.island.recorder.R
import kotlinx.parcelize.Parcelize

/**
 * Video quality tiers - dimensions computed at runtime based on device screen
 */
enum class VideoQuality(val targetShortSide: Int, @param:StringRes val tierLabelResId: Int) {
    NATIVE(0, R.string.quality_native),
    FHD(1080, R.string.quality_fhd),
    HIGH(720, R.string.quality_high),
    MEDIUM(480, R.string.quality_medium),
    LOW(360, R.string.quality_low);

    fun computeDimensions(screenW: Int, screenH: Int): Pair<Int, Int> {
        val shortSide = minOf(screenW, screenH)
        val longSide = maxOf(screenW, screenH)

        if (targetShortSide == 0 || targetShortSide >= shortSide) {
            return Pair(screenW, screenH)
        }

        val scale = targetShortSide.toFloat() / shortSide
        val newShort = (targetShortSide / 2) * 2
        val newLong = ((longSide * scale).toInt() / 2) * 2

        return if (screenW < screenH) Pair(newShort, newLong) else Pair(newLong, newShort)
    }
}

/**
 * Video bitrate options
 */
enum class VideoBitrate(val bps: Int, @param:StringRes val labelResId: Int) {
    AUTO(0, R.string.bitrate_auto),
    BITRATE_1M(1_000_000, R.string.bitrate_1m),
    BITRATE_4M(4_000_000, R.string.bitrate_4m),
    BITRATE_6M(6_000_000, R.string.bitrate_6m),
    BITRATE_8M(8_000_000, R.string.bitrate_8m),
    BITRATE_16M(16_000_000, R.string.bitrate_16m),
    BITRATE_24M(24_000_000, R.string.bitrate_24m),
    BITRATE_32M(32_000_000, R.string.bitrate_32m),
    BITRATE_50M(50_000_000, R.string.bitrate_50m),
    BITRATE_100M(100_000_000, R.string.bitrate_100m)
}

/**
 * Screen orientation for recording
 */
enum class ScreenOrientation(@param:StringRes val labelResId: Int) {
    AUTO(R.string.orientation_auto),
    PORTRAIT(R.string.orientation_portrait),
    LANDSCAPE(R.string.orientation_landscape)
}

/**
 * Frame rate options
 */
enum class FrameRate(val fps: Int, @param:StringRes val labelResId: Int) {
    AUTO(0, R.string.fps_auto),
    FPS_15(15, R.string.fps_15),
    FPS_24(24, R.string.fps_24),
    FPS_30(30, R.string.fps_30),
    FPS_48(48, R.string.fps_48),
    FPS_60(60, R.string.fps_60),
    FPS_90(90, R.string.fps_90),
    FPS_120(120, R.string.fps_120);

    fun bitrateFps(autoFrameRateFps: Int): Int =
        if (fps > 0) fps else autoFrameRateFps.coerceAtLeast(1)
}

/**
 * Audio source configuration
 */
enum class AudioSource(@param:StringRes val labelResId: Int) {
    NONE(R.string.audio_none),
    INTERNAL(R.string.audio_internal),
    MICROPHONE(R.string.audio_microphone),
    BOTH(R.string.audio_both)
}

/**
 * Video codec options
 */
enum class VideoCodec(
    val mimeType: String,
    @param:StringRes val labelResId: Int,
    val isHdrEnabled: Boolean = false
) {
    H264(android.media.MediaFormat.MIMETYPE_VIDEO_AVC, R.string.codec_h264),
    H265(android.media.MediaFormat.MIMETYPE_VIDEO_HEVC, R.string.codec_h265),
    H265_HDR(android.media.MediaFormat.MIMETYPE_VIDEO_HEVC, R.string.codec_h265_hdr, true)
}

/**
 * Quick settings tile icon style
 */
enum class TileStyle(@param:StringRes val labelResId: Int) {
    DEFAULT(R.string.tile_style_app_icon),
    APP_ICON(R.string.tile_style_default)
}

/**
 * Recording configuration settings
 */
@Parcelize
data class RecordingSettings(
    val videoQuality: VideoQuality = VideoQuality.FHD,
    val videoBitrate: VideoBitrate = VideoBitrate.AUTO,
    val screenOrientation: ScreenOrientation = ScreenOrientation.AUTO,
    val frameRate: FrameRate = FrameRate.AUTO,
    val audioSource: AudioSource = AudioSource.NONE,
    val videoCodec: VideoCodec = VideoCodec.H264,
    val showTouches: Boolean = false,
    val stopOnLockScreen: Boolean = false,
    val bypassFocusIsland: Boolean = false,
    val tileStyle: TileStyle = TileStyle.DEFAULT
) : Parcelable {
    /**
     * Calculate bitrate: fixed value if user chose one, otherwise auto-compute
     */
    fun calculateBitrate(width: Int, height: Int, autoFrameRateFps: Int): Int {
        if (videoBitrate != VideoBitrate.AUTO) {
            return videoBitrate.bps
        }
        val pixels = width * height
        val motionFactor = 1.5f
        val qualityFactor = 0.12f
        return (pixels * frameRate.bitrateFps(autoFrameRateFps) * motionFactor * qualityFactor).toInt()
    }
}

