package com.justcal.app.data

import androidx.core.content.FileProvider

/** A distinct Android component so update and diagnostics path metadata cannot collide. */
class UpdateFileProvider : FileProvider()
