package com.opdownloader.app.data

/** Holds a media analysis between the analyzer screen and the quality picker. */
object PendingSelection {
    var video: AnalysisResult.Video? = null
    var option: QualityOption? = null

    fun clear() {
        video = null
        option = null
    }
}
