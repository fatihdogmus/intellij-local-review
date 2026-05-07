package dev.agentreview.intellij.util

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private val displayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun nowIso(): String = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

fun nowDisplay(): String = OffsetDateTime.now().format(displayFormatter)

fun displayTimestamp(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).format(displayFormatter)
}.getOrElse { iso }
