package com.justcal.app.ocr

/** Runtime experiments stay inside the adapter; neither UI nor persistence chooses a backend. */
internal data class OcrRuntimeConfig(
    val threads: Int = 4,
    val reuseSessions: Boolean = false,
    val progressive: Boolean = true,
    val profile: Boolean = false,
    val refineEnergy: Boolean = true,
) {
    init { require(threads in 1..4) }
}
