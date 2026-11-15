package maryk.lib.bytes

internal actual fun ByteArray.decodeUtf8StrictPlatform(startIndex: Int, endIndex: Int): String =
    decodeUtf8StrictFallback(startIndex, endIndex)
