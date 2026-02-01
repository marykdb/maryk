package io.maryk.app.data

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import maryk.core.clock.HLC

internal fun formatHlcTimestamp(version: ULong): String {
    val epochMillis = HLC(version).toPhysicalUnixTime().toLong()
    val dateTime = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return buildString {
        append(dateTime.year.toString().padStart(4, '0'))
        append('-')
        append(dateTime.month.number.toString().padStart(2, '0'))
        append('-')
        append(dateTime.day.toString().padStart(2, '0'))
        append(' ')
        append(dateTime.hour.toString().padStart(2, '0'))
        append(':')
        append(dateTime.minute.toString().padStart(2, '0'))
        append(':')
        append(dateTime.second.toString().padStart(2, '0'))
    }
}