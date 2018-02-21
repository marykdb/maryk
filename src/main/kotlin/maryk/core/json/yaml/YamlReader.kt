package maryk.core.json.yaml

import maryk.core.json.ExceptionWhileReadingJson
import maryk.core.json.InvalidJsonContent
import maryk.core.json.IsJsonLikeReader
import maryk.core.json.JsonToken

@Suppress("FunctionName")
fun YamlReader(reader: () -> Char) : IsJsonLikeReader =
    YamlReaderImpl(reader)

/** Internal interface for the Yaml Reader functionality */
internal interface IsYamlReader {
    /** Is last character which was read */
    val lastChar: Char
    /** Holds the current char reader */
    var currentReader: YamlCharReader

    /** Reads next Char */
    fun read()
}

/** Reads YAML from the supplied [reader] */
internal class YamlReaderImpl(
    private val reader: () -> Char
) : IsJsonLikeReader, IsYamlReader {
    override var currentToken: JsonToken = JsonToken.StartJSON

    override var lastChar: Char = '\u0000'
    override var currentReader: YamlCharReader = DocumentStartReader(this)

    private var unclaimedIndenting: Int? = null
    private var hasException: Boolean = false

    override fun nextToken(): JsonToken {
        if (this.hasException) {
            return this.currentReader.handleReaderInterrupt()
        }

        currentToken = try {
            this.currentReader.let {
                if (this.unclaimedIndenting != null && it is IsYamlCharWithIndentsReader) {
                    val remainder = it.indentCount() - this.unclaimedIndenting!!
                    if (remainder > 0) {
                        it.endIndentLevel(this.unclaimedIndenting!!)
                    } else if (remainder == 0) {
                        this.unclaimedIndenting = null
                        it.continueIndentLevel()
                    } else {
                        // Indents are only left over on closing indents so should never be lower
                        throw InvalidYamlContent("Lower indent found than previous started indents")
                    }
                } else {
                    it.readUntilToken()
                }
            }
        } catch (e: ExceptionWhileReadingJson) {
            this.hasException = true
            currentReader.handleReaderInterrupt()
        }
        return currentToken
    }

    override fun skipUntilNextField() {
        TODO("not implemented")
    }

    override fun read() = try {
        lastChar = reader()
    } catch (e: Throwable) { // Reached end or something bad happened
        throw ExceptionWhileReadingJson()
    }

    fun hasUnclaimedIndenting(indentCount: Int?) {
        this.unclaimedIndenting = indentCount
    }
}

/** Exception for invalid Yaml */
class InvalidYamlContent internal constructor(
    description: String
): InvalidJsonContent(description)

