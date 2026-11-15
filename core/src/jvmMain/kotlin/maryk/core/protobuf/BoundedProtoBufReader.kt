package maryk.core.protobuf

private class ProtoBufReaderCallback(val owner: BoundedProtoBufReader) : () -> Byte {
    override fun invoke(): Byte = owner.readByte()
}

internal actual fun bindProtoBufReader(owner: BoundedProtoBufReader): () -> Byte = ProtoBufReaderCallback(owner)
internal actual fun protoBufReaderOwner(reader: () -> Byte): BoundedProtoBufReader? =
    (reader as? ProtoBufReaderCallback)?.owner
