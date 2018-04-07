package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.extensions.isSpacing
import maryk.core.json.JsonToken
import maryk.core.json.MapType
import maryk.core.json.TokenType

private enum class MapState {
    NEW_PAIR, KEY_FOUND
}

/** Reader for Map Items */
internal class MapItemsReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    private var indentToAdd: Int = 0
) : YamlCharWithParentAndIndentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var state: MapState = MapState.KEY_FOUND
    private var isStarted = false

    private val fieldNames = mutableListOf<String?>()

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        return if (!this.isStarted) {
//            this.lineReader(this, this.lastChar.isLineBreak())

            this.isStarted = true
            return tag?.let {
                val mapType = it as? MapType ?: throw InvalidYamlContent("Can only use map tags on maps")
                JsonToken.StartObject(mapType)
            } ?: JsonToken.SimpleStartObject
        } else {
            if (this.lastChar.isLineBreak()) {
                val currentIndentCount = this.yamlReader.skipEmptyLinesAndCommentsAndCountIndents()
                val readerIndentCount = this.indentCount()
                if (currentIndentCount < readerIndentCount) {
                    return this.endIndentLevel(currentIndentCount, tag, null)
                } else if (currentIndentCount == readerIndentCount) {
                    this.continueIndentLevel(extraIndent, tag)
                } else {
                    this.newLineReader(true, tag, currentIndentCount - readerIndentCount, this::jsonTokenCreator)
                }
            } else {
                this.newLineReader(this.lastChar.isLineBreak(), tag, extraIndent, this::jsonTokenCreator)
            }
        }
    }

    override fun foundMap(tag: TokenType?, startedAtIndent: Int): JsonToken? {
        if (startedAtIndent > 1) {
            return MapItemsReader(
                this.yamlReader,
                this,
                indentToAdd = startedAtIndent
            ).let {
                this.currentReader = it
                it.readUntilToken(0, tag)
            }
        }

        if (this.state == MapState.KEY_FOUND) {
            throw InvalidYamlContent("Already found mapping key. No other : allowed")
        }

        this.state = MapState.KEY_FOUND
        println("🔑 ${this.state}")
        return null
    }

    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        checkAndCreateFieldName(this.fieldNames, fieldName, isPlainStringReader)

    override fun isWithinMap() = true

    override fun continueIndentLevel(extraIndent: Int, tag: TokenType?): JsonToken {
        this.currentReader = this
        this.state = MapState.NEW_PAIR
        println("🆕 ${this.state}")

        return this.newLineReader(true, tag, extraIndent, this::jsonTokenCreator)
    }

    private fun jsonTokenCreator(value: String?, isPlainStringReader: Boolean, tag: TokenType?): JsonToken {
        if (this.state == MapState.KEY_FOUND) {
            if(this.parentReader is ExplicitMapKeyReader<*>) {
//                this.indentToAdd -= 1
            }
            this.state = MapState.NEW_PAIR
            return createYamlValueToken(value, tag, isPlainStringReader)
        }

        while (this.lastChar.isSpacing()) {
            read()
        }

        if (this.parentReader is ExplicitMapKeyReader<*> && this.currentReader != this) {
            return this.checkAndCreateFieldName(value, isPlainStringReader)
        } else if (this.lastChar == ':' && !this.yamlReader.hasUnclaimedIndenting()) {
            read()
            if (this.lastChar.isWhitespace()) {
                if (this.lastChar.isLineBreak()) {
                    IndentReader(this.yamlReader, this).let {
                        this.currentReader = it
                    }
                }

                val fieldName = this.checkAndCreateFieldName(value, isPlainStringReader)
                return this.foundMap(tag, 0)?.let {
                    this.yamlReader.pushToken(fieldName)
                    it
                } ?: fieldName
            } else {
                throw InvalidYamlContent("There should be whitespace after :")
            }
        }

        this.state = MapState.NEW_PAIR
        return createYamlValueToken(value, tag, isPlainStringReader)
    }

    override fun indentCount(): Int = this.indentToAdd + if(this.parentReader is MapItemsReader<*>) this.parentReader.indentCount() else this.parentReader.indentCountForChildren()

    override fun indentCountForChildren() = this.indentCount() + if(this.state == MapState.KEY_FOUND) 1 else 0

    override fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ): JsonToken {
        if (indentCount == this.indentCount()) {
            // this reader should handle the read
            this.currentReader = this
            return if (tokenToReturn != null) {
                this.yamlReader.setUnclaimedIndenting(indentCount)
                tokenToReturn()
            } else {
                this.yamlReader.setUnclaimedIndenting(null)
                this.continueIndentLevel(0, tag)
            }
        }

        this.parentReader.childIsDoneReading(false)
        return if (indentToAdd > 0) {
            this.yamlReader.setUnclaimedIndenting(indentCount)
            tokenToReturn?.let {
                this.yamlReader.pushToken(JsonToken.EndObject)
                return it()
            }
            JsonToken.EndObject
        } else {
            val returnFunction = tokenToReturn?.let {
                this.yamlReader.pushToken(JsonToken.EndObject)
                it
            } ?: { JsonToken.EndObject }

            this.parentReader.endIndentLevel(indentCount, tag, returnFunction)
        }
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.currentReader = this.parentReader
        return JsonToken.EndObject
    }
}
