package maryk.core.properties.types

import kotlinx.datetime.LocalDate
import maryk.core.extensions.bytes.initInt
import maryk.core.extensions.bytes.writeBytes
import maryk.lib.time.Date.ofEpochDay
import maryk.lib.time.epochDay

internal fun LocalDate.writeBytes(writer: (byte: Byte) -> Unit) {
    this.epochDay.writeBytes(writer)
}

/** Reads a date from bytes [reader] */
internal fun localDateFromByteReader(reader: () -> Byte) = ofEpochDay(
    initInt(reader)
)
