package maryk.core.properties.types

@Suppress("unused")
class Key<out DO: Any>(bytes: ByteArray) : Bytes(bytes)