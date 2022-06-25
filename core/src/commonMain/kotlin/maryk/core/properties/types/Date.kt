package maryk.core.properties.types

import kotlinx.datetime.LocalDate
import maryk.core.extensions.bytes.initInt
import maryk.core.extensions.bytes.writeBytes

internal fun LocalDate.writeBytes(writer: (byte: Byte) -> Unit) {
    this.toEpochDays().writeBytes(writer)
}

/** Reads a date from bytes [reader] */
internal fun localDateFromByteReader(reader: () -> Byte) = LocalDate.fromEpochDays(
    initInt(reader)
)
