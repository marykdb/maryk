package maryk.core.models.serializers

import maryk.json.IsJsonLikeReader
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** Custom decoders must finish their own container before returning decoded values. */
internal fun IsJsonLikeReader.requireJsonToken(expected: JsonToken) {
    if (currentToken != expected) throw ParseException("Expected $expected, got $currentToken")
}
