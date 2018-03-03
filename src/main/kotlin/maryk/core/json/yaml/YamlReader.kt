package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
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
    var version: String? = null

    override var currentToken: JsonToken = JsonToken.StartDocument

    override var lastChar: Char = '\u0000'
    override var currentReader: YamlCharReader = DocumentReader(this)

    private var unclaimedIndenting: Int? = null
    private var hasException: Boolean = false
    internal val tags: MutableMap<String, String> = mutableMapOf()

    override fun nextToken(): JsonToken {
        if (this.hasException) {
            return this.currentReader.handleReaderInterrupt()
        }

        currentToken = try {
            this.currentReader.let {
                if (this.unclaimedIndenting != null && it is IsYamlCharWithIndentsReader) {
                    // Skip stray comments and read until first relevant character
                    if (this.lastChar == '#') {
                        skipComments()
                    }

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

    private fun skipComments() {
        while (!this.lastChar.isLineBreak()) {
            read()
        }
        var currentIndentCount = 0
        while (this.lastChar.isWhitespace()) {
            if (this.lastChar.isLineBreak()) {
                currentIndentCount = 0
            } else {
                currentIndentCount++
            }
            read()
            // Skip comments since they can start early
            if (this.lastChar == '#') {
                while (!this.lastChar.isLineBreak()) {
                    read()
                }
            }
        }
        this.unclaimedIndenting = currentIndentCount
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

