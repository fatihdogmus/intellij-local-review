package dev.fatihdogmus.agenticreview.util

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

fun nowIso(): String = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
