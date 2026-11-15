package maryk.core.protobuf

// JS functions cannot implement a Kotlin class interface; retain ownership on the private callback.
internal actual fun bindProtoBufReader(owner: BoundedProtoBufReader): () -> Byte {
    val reader = { owner.readByte() }
    reader.asDynamic().__marykProtoBufReader = owner
    return reader
}

internal actual fun protoBufReaderOwner(reader: () -> Byte): BoundedProtoBufReader? =
    reader.asDynamic().__marykProtoBufReader as? BoundedProtoBufReader
