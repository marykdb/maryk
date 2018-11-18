package maryk.core.properties.types

import maryk.core.extensions.bytes.initInt
import maryk.core.extensions.bytes.writeBytes

typealias Date = maryk.lib.time.Date

internal fun maryk.lib.time.Date.writeBytes(writer: (byte: Byte) -> Unit) {
    this.epochDay.writeBytes(writer)
}

/** Reads a date from bytes [reader] */
internal fun maryk.lib.time.Date.Companion.fromByteReader(reader: () -> Byte) = maryk.lib.time.Date.ofEpochDay(
    initInt(reader)
)
