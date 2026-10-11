package com.justcal.app.ocr

import kotlin.math.abs

/** Height-relative row grouping works at both detector scale and full-resolution coordinates. */
object LabelReadingOrder {
    fun sort(lines: List<OcrLine>): List<OcrLine> {
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in lines.sortedBy { (it.top + it.bottom) / 2 }) {
            val row = rows.lastOrNull()
            val anchor = row?.firstOrNull()
            if (anchor != null && abs((line.top + line.bottom - anchor.top - anchor.bottom) / 2) <
                minOf(line.height, anchor.height) * 0.45f) row.add(line)
            else rows.add(mutableListOf(line))
        }
        return rows.flatMap { it.sortedBy(OcrLine::left) }
    }
}
