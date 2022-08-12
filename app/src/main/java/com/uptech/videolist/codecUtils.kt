package com.uptech.videolist

import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

fun availableCodecsNum(): Int =
  listOf(MimeTypes.VIDEO_MP4, MimeTypes.VIDEO_H264, MimeTypes.VIDEO_MP4V).map { mimeType ->
    MediaCodecUtil.getDecoderInfos(mimeType, false, false)
  }.flatten()
    .filter { codecInfo -> codecInfo.hardwareAccelerated }
    .map { it.maxSupportedInstances }
    .reduce { acc, maxInstances -> acc + maxInstances }