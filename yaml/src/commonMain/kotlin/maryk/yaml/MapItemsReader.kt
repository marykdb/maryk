package maryk.yaml

import maryk.json.JsonToken
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.NullValue
import maryk.json.JsonToken.SimpleStartObject
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartComplexFieldName
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Value
import maryk.json.MapType
import maryk.json.TokenType
import maryk.json.ValueType.Null
import maryk.lib.extensions.isLineBreak
import maryk.lib.extensions.isSpacing
import maryk.yaml.MapState.KEY_FOUND
import maryk.yaml.MapState.NEW_PAIR
import maryk.yaml.MapState.VALUE_FOUND

private enum class MapState {
    NEW_PAIR, KEY_FOUND, VALUE_FOUND
}

/** Reader for Map Items */
internal class MapItemsReader<out P : IsYamlCharWithIndentsReader>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    private var indentToAdd: Int = 0
) : YamlCharWithParentAndIndentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader {
    private var state: MapState = KEY_FOUND // Because is always created after finding key
    private var isStarted = false

    private val fieldNames = mutableListOf<String?>()

    private var stateWasSetOnRead: Boolean = false

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        this.stateWasSetOnRead = false
        return if (!this.isStarted) {
            this.isStarted = true
            return tag?.let {
                val mapType = it as? MapType ?: throw InvalidYamlContent("Can only use map tags on maps")
                StartObject(mapType)
            } ?: SimpleStartObject
        } else {
            if (this.lastChar.isLineBreak()) {
                val currentIndentCount = this.yamlReader.skipEmptyLinesAndCommentsAndCountIndents()
                val readerIndentCount = this.indentCount()
                if (currentIndentCount < readerIndentCount) {
                    this.endIndentLevel(currentIndentCount, tag, null)
                } else {
                    if (currentIndentCount == readerIndentCount && this.state == VALUE_FOUND) {
                        this.state = NEW_PAIR
                    }
                    this.selectReaderAndRead(true, tag, currentIndentCount - readerIndentCount, this::jsonTokenCreator)
                        .also {
                            return checkAndSetState(currentIndentCount == readerIndentCount, tag, it)
                        }
                }
            } else {
                this.selectReaderAndRead(false, tag, extraIndent, this::jsonTokenCreator).also(::setState)
            }
        }
    }

    internal fun setState(it: JsonToken) {
        if (this.stateWasSetOnRead) {
            return
        }

        if (it is FieldName || it == StartComplexFieldName) {
            if (this.state == KEY_FOUND) {
                throw InvalidYamlContent("Already found mapping key. No other : allowed")
            }
            this.state = KEY_FOUND
        }
        // Set value found on any return when key was found
        else if (this.state == KEY_FOUND) {
            this.state = VALUE_FOUND
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

    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean): FieldName {
        return checkAndCreateFieldName(this.fieldNames, fieldName, isPlainStringReader)
    }

    override fun continueIndentLevel(extraIndent: Int, tag: TokenType?): JsonToken {
        this.stateWasSetOnRead = false
        this.currentReader = this
        val token = this.selectReaderAndRead(true, tag, extraIndent, this::jsonTokenCreator)

        return checkAndSetState(extraIndent == 0, tag, token)
    }

    private fun jsonTokenCreator(
        value: String?,
        isPlainStringReader: Boolean,
        tag: TokenType?,
        extraIndent: Int
    ): JsonToken {
        while (this.lastChar.isSpacing() && !this.yamlReader.hasException) {
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
        } else if (this.state == KEY_FOUND) {
            return createYamlValueToken(value, tag, isPlainStringReader)
        } else {
            this.yamlReader.pushToken(createYamlValueToken(null, Null, false))
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

        // Return tag placeholders if tag was encountered
        if (tag != null) {
            this.yamlReader.setUnclaimedIndenting(indentCount)
            tokenToReturn?.let {
                this.yamlReader.pushTokenAsFirst(it())
            }

            return this.createTokensFittingTag(tag)
        }

        if (this.state == KEY_FOUND && tokenToReturn == null) {
            // Return null value if no value was returned yet
            this.state = VALUE_FOUND
            this.yamlReader.setUnclaimedIndenting(indentCount)
            return NullValue
        }

        this.currentReader = this.parentReader
        return if (indentToAdd > 0) {
            this.yamlReader.setUnclaimedIndenting(indentCount)
            tokenToReturn?.let {
                this.yamlReader.pushToken(EndObject)
                return it()
            }
            EndObject
        } else {
            val returnFunction = tokenToReturn?.let {
                this.yamlReader.pushToken(EndObject)
                it
            } ?: { EndObject }

            this.parentReader.endIndentLevel(indentCount, tag, returnFunction)
        }
    }

    private fun checkAndSetState(
        onZeroExtraIndent: Boolean,
        tag: TokenType?,
        it: JsonToken
    ): JsonToken {
        if (this.stateWasSetOnRead) {
            return it
        }

        if (onZeroExtraIndent
            && this.state == KEY_FOUND
            && it !is StartArray
            && it !is Value<*>
        ) {
            this.state = VALUE_FOUND
            this.setState(it)

            this.stateWasSetOnRead = true

            return if (this.state == KEY_FOUND) {
                this.yamlReader.pushTokenAsFirst(it)
                this.createTokensFittingTag(tag)
            } else {
                it
            }
        } else {
            this.state = NEW_PAIR
            this.setState(it)
            return it
        }
    }

    override fun handleReaderInterrupt(): JsonToken {
        if (this.state == KEY_FOUND) {
            // Return null value if no value was returned yet
            this.state = VALUE_FOUND
            return NullValue
        }

        this.currentReader = this.parentReader
        return EndObject
    }
}
