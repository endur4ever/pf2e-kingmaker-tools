package com.foundryvtt.core.documents

import kotlinx.js.JsPlainObject

@JsPlainObject
external interface Thumbnail {
    val thumb: String
    val width: Int
    val height: Int
    // Only these three are relied on. Other fields of Scene#createThumbnail's result were not
    // verified against v14.363 (2026-09-02) and are left unbound rather than guessed.
}