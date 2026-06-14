package maryk.core.extensions.bytes

internal fun ByteArray.throwingReader(): () -> Byte {
    var index = 0
    return {
        this.getOrNull(index++) ?: throw NoSuchElementException("EOF")
    }
}
