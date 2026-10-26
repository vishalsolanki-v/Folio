package com.folio.app.core.model

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter

/** "2.4 MB · 10 min. ago · WhatsApp" (plan §2.1). Locale-aware size and time. */
fun fileSubtitle(context: Context, sizeBytes: Long, time: Long, now: Long, source: String? = null): String {
    val size = if (sizeBytes >= 0) Formatter.formatShortFileSize(context, sizeBytes) else null
    val whenText = when {
        time <= 0 -> null
        now - time in 0 until DateUtils.MINUTE_IN_MILLIS -> "Just now"
        else -> DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
    }
    return listOfNotNull(size, whenText, source).joinToString(" · ")
}
