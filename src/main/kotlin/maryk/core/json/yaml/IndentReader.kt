package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.MapType
import maryk.core.json.TokenType

/** Reads indents on a new line until a char is found */
internal class IndentReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : maryk.core.json.yaml.YamlCharReader,
              P : maryk.core.json.yaml.IsYamlCharWithChildrenReader,
              P : maryk.core.json.yaml.IsYamlCharWithIndentsReader
{
    private var indentCounter = -1
    private var mapKeyFound: Boolean = false
    private val fieldNames = mutableListOf<String?>()

    // Should not be called
    override fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader =
        this.parentReader.newIndentLevel(indentCount, parentReader, tag)

    override fun continueIndentLevel(tag: TokenType?) =
        LineReader(
            this.yamlReader,
            this
        ).let {
            this.currentReader = it
            it.readUntilToken(tag)
        }

    override fun foundMap(isExplicitMap: Boolean, tag: TokenType?): JsonToken? =
        if (!this.mapKeyFound) {
            this.mapKeyFound = true
            tag?.let {
                (it as? MapType)?.let {
                    JsonToken.StartObject(it)
                } ?: throw InvalidYamlContent("Cannot use non map tags on maps")
            } ?: JsonToken.SimpleStartObject
        } else {
            null
        }

    override fun isWithinMap() = this.mapKeyFound

    override fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ): JsonToken {
        if (this.mapKeyFound) {
            this.yamlReader.setUnclaimedIndenting(indentCount)
            this.mapKeyFound = false

            if (indentCount < this.indentCount()) {
                this.parentReader.childIsDoneReading(true)
            }

            tokenToReturn?.let {
                this.yamlReader.setUnclaimedIndenting(indentCount)
                this.yamlReader.pushToken(JsonToken.EndObject)
                return it()
            }
            return JsonToken.EndObject
        }

        this.parentReader.childIsDoneReading(true)

        tokenToReturn?.let {
            this.yamlReader.setUnclaimedIndenting(indentCount)
            return it()
        }

        @Suppress("UNCHECKED_CAST")
        (this.currentReader as P).let {
            return if (it.indentCount() == indentCount) {
                // found right level so continue
                this.yamlReader.setUnclaimedIndenting(null)
                if (it is IndentReader<*>) {
                    it.continueIndentLevel(null)
                } else {
                    it.readUntilToken()
                }
            } else {
                it.endIndentLevel(indentCount, tag, null)
            }
        }
    }

    override fun readUntilToken(tag: TokenType?): JsonToken {
        val currentIndentCount = this.yamlReader.skipEmptyLinesAndCommentsAndCountIndents()

        if (this.indentCounter == -1) {
            this.indentCounter = currentIndentCount

            if (this.parentReader is DocumentReader) {
                this.parentReader.setIndent(this.indentCounter)
            }
        }

        val parentIndentCount = this.parentReader.indentCount()
        return when(currentIndentCount) {
            parentIndentCount -> this.parentReader.continueIndentLevel(tag)
            in 0 until parentIndentCount -> {
                this.parentReader.childIsDoneReading(false)

                val tokenToReturn: (() -> JsonToken)? = if (this.mapKeyFound) {
                    this.mapKeyFound = false
                    { JsonToken.EndObject }
                } else {
                    null
                }

                this.parentReader.endIndentLevel(currentIndentCount, tag, tokenToReturn)
            }
            else -> if (currentIndentCount == this.indentCounter){
                this.parentReader.newIndentLevel(currentIndentCount, this, tag)
            } else {
                throw InvalidYamlContent("Cannot have a new indent level which is lower than current")
            }
        }
    }

    override fun indentCount() = this.indentCounter

    override fun indentCountForChildren() = this.indentCount()

    override fun childIsDoneReading(closeLineReader: Boolean) {
        this.currentReader = this
    }

    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        checkAndCreateFieldName(this.fieldNames, fieldName, isPlainStringReader)

    override fun handleReaderInterrupt(): JsonToken {
        if (this.mapKeyFound) {
            this.mapKeyFound = false
            return JsonToken.EndObject
        }
        this.parentReader.childIsDoneReading(false)
        return parentReader.handleReaderInterrupt()
    }
}
