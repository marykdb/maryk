package maryk.yaml

import maryk.json.JsonToken
import maryk.json.MapType
import maryk.json.TokenType
import maryk.json.ValueType
import maryk.lib.extensions.isLineBreak
import maryk.lib.extensions.isSpacing

private enum class MapState {
    NEW_PAIR, KEY_FOUND, VALUE_FOUND
}

/** Reader for Map Items */
internal class MapItemsReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    private var indentToAdd: Int = 0
) : YamlCharWithParentAndIndentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader
        where P : YamlCharReader,
              P : IsYamlCharWithIndentsReader
{
    private var state: MapState = MapState.KEY_FOUND // Because is always created after finding key
    private var isStarted = false

    private val fieldNames = mutableListOf<String?>()

    private var stateWasSetOnRead: Boolean = false

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        this.stateWasSetOnRead = false
        return if (!this.isStarted) {
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
                    this.endIndentLevel(currentIndentCount, tag, null)
                } else {
                    if (currentIndentCount == readerIndentCount && this.state == MapState.VALUE_FOUND) {
                        this.state = MapState.NEW_PAIR
                    }
                    this.selectReaderAndRead(true, tag, currentIndentCount - readerIndentCount, this::jsonTokenCreator).also {
                        this.setState(it)
                    }
                }
            } else {
                this.selectReaderAndRead(false, tag, extraIndent, this::jsonTokenCreator).also {
                    this.setState(it)
                }
            }
        }
    }

    internal fun setState(it: JsonToken) {
        if(this.stateWasSetOnRead) {
            return
        }

        if (it is JsonToken.FieldName) {
            if (this.state == MapState.KEY_FOUND) {
                throw InvalidYamlContent("Already found mapping key. No other : allowed")
            }
            this.state = MapState.KEY_FOUND
        }
        // Set value found on any return when key was found
        else if (this.state == MapState.KEY_FOUND) {
            this.state = MapState.VALUE_FOUND
        }

        this.stateWasSetOnRead = true
    }

    override fun foundMap(tag: TokenType?, startedAtIndent: Int): JsonToken? {
        if (startedAtIndent > 0) {
            return MapItemsReader(
                this.yamlReader,
                this,
                indentToAdd = startedAtIndent
            ).let {
                this.currentReader = it
                it.readUntilToken(0, tag)
            }
        }
        return null
    }

    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean): JsonToken.FieldName {
        return checkAndCreateFieldName(this.fieldNames, fieldName, isPlainStringReader)
    }

    override fun continueIndentLevel(extraIndent: Int, tag: TokenType?): JsonToken {
        this.stateWasSetOnRead = false
        this.currentReader = this
        if (this.state == MapState.VALUE_FOUND) {
            this.state = MapState.NEW_PAIR
        }
        return this.selectReaderAndRead(true, tag, extraIndent, this::jsonTokenCreator).also {
            setState(it)
        }
    }

    internal fun jsonTokenCreator(value: String?, isPlainStringReader: Boolean, tag: TokenType?, extraIndent: Int): JsonToken {
        while (this.lastChar.isSpacing()) {
            read()
        }

        if (this.lastChar == ':' && !this.yamlReader.hasUnclaimedIndenting()) {
            read()
            if (this.lastChar.isWhitespace()) {
                if (!this.lastChar.isLineBreak()) {
                    read()
                }

                return this.foundMap(tag, extraIndent)?.let {
                    this.yamlReader.pushToken(
                        this.checkAndCreateFieldName(value, isPlainStringReader)
                    )
                    it
                } ?: this.checkAndCreateFieldName(value, isPlainStringReader)
            } else {
                throw InvalidYamlContent("There should be whitespace after :")
            }
        } else if (this.state == MapState.KEY_FOUND) {
            return createYamlValueToken(value, tag, isPlainStringReader)
        } else {
            this.yamlReader.pushToken(createYamlValueToken(null, ValueType.Null, false))
            return this.checkAndCreateFieldName(value, isPlainStringReader)
        }
    }

    override fun indentCount(): Int = this.indentToAdd + this.parentReader.indentCount()

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

        this.currentReader = this.parentReader
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
