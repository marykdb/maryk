package maryk.lib.bytes

import kotlin.text.CharacterCodingException

private val strictUtf8Decoder: dynamic = js("typeof TextDecoder === 'undefined' ? null : new TextDecoder('utf-8', {fatal: true, ignoreBOM: true})")

internal actual fun ByteArray.decodeUtf8StrictPlatform(startIndex: Int, endIndex: Int): String {
    if (strictUtf8Decoder == null) return decodeUtf8StrictFallback(startIndex, endIndex)
    return try {
        // subarray is a view. The native decoder validates and copies into its
        // immutable string without Kotlin's per-character StringBuilder objects.
        strictUtf8Decoder.decode(asDynamic().subarray(startIndex, endIndex)) as String
    } catch (cause: Throwable) {
        if (js("cause instanceof TypeError").unsafeCast<Boolean>()) throw CharacterCodingException()
        throw cause
    }
}
