package com.boulderbuddy.ghost.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import com.boulderbuddy.ghost.GhostTuning
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Einzel-Frame-Zugriff für die Anker-Standbilder (M2). Dekodiert im selben skalierten
 * Koordinatenraum wie die Pose-Extraktion ([scaledFrameAt]), damit getippte Anker und
 * Keypoints direkt zusammenpassen.
 */
class GhostFrameDecoder @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    suspend fun frameAt(videoUri: String, timeMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri.toUri())
            retriever.scaledFrameAt(timeMs, GhostTuning.POSE_INPUT_LONG_SIDE_PX)
        } finally {
            retriever.release()
        }
    }
}
