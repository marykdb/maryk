package maryk.core.json.yaml

import maryk.core.json.ExceptionWhileReadingJson
import maryk.core.json.IsJsonLikeReader
import maryk.core.json.JsonToken

/** Reads YAML from the supplied [reader] */
class YamlReader(
    private val reader: () -> Char
) : IsJsonLikeReader {
    override var currentToken: JsonToken = JsonToken.StartJSON

    internal var lastChar: Char = ' '
    internal var currentReader: YamlCharReader = DocumentStartReader(this)

    override fun nextToken(): JsonToken {
        currentToken = try {
            currentReader.readUntilToken()
        } catch (e: ExceptionWhileReadingJson) {
            currentReader.handleReaderInterrupt()
        }
        return currentToken
    }

    override fun skipUntilNextField() {
        TODO("not implemented")
    }

    internal fun read() = try {
        lastChar = reader()
    } catch (e: Throwable) { // Reached end or something bad happened
        throw ExceptionWhileReadingJson()
    }
}


