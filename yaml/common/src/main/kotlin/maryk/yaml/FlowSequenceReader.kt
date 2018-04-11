package maryk.yaml

import maryk.json.ArrayType
import maryk.json.JsonToken
import maryk.json.MapType
import maryk.json.TokenType

private enum class FlowSequenceState {
    START, VALUE_START, EXPLICIT_KEY, COMPLEX_KEY, MAP_VALUE_AFTER_COMPLEX_KEY, KEY, VALUE, MAP_VALUE, MAP_END, STOP
}

/** Reader for flow sequences [item1, item2, item3] */
internal class FlowSequenceReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    private val indentToAdd: Int
) : YamlCharWithParentAndIndentReader<P>(yamlReader, parentReader)
        where P : YamlCharReader,
              P : IsYamlCharWithIndentsReader
{
    private var state = FlowSequenceState.START
    private var cachedCall: (() -> JsonToken)? = null

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        val stateAtStart = this.state
        if (this.state == FlowSequenceState.EXPLICIT_KEY) {
            this.state = FlowSequenceState.KEY
        }

        return when(this.state) {
            FlowSequenceState.START -> {
                this.state = FlowSequenceState.VALUE_START
                tag?.let {
                    JsonToken.StartArray(
                        it as? ArrayType ?: throw InvalidYamlContent("Can only use sequence tags on sequences")
                    )
                } ?: JsonToken.SimpleStartArray
            }
            FlowSequenceState.COMPLEX_KEY -> this.jsonTokenCreator(null, false, null, 0)
            else -> {
                while(this.lastChar.isWhitespace()) {
                    read()
                }

                return when(this.lastChar) {
                    '\'' -> this.singleQuoteString(tag, extraIndent, this::jsonTokenCreator)
                    '\"' -> this.doubleQuoteString(tag, extraIndent, this::jsonTokenCreator)
                    '{' -> this.flowMapReader(tag, 0).let(this::checkComplexFieldAndReturn)
                    '[' -> this.flowSequenceReader(tag, 0).let(this::checkComplexFieldAndReturn)
                    '!' -> this.tagReader { this.readUntilToken(extraIndent, it) }
                    '&' -> this.anchorReader { this.readUntilToken(extraIndent, tag) }
                    '*' -> this.aliasReader(PlainStyleMode.FLOW_SEQUENCE)
                    '-' -> {
                        read()
                        if (this.lastChar.isWhitespace()) {
                            throw InvalidYamlContent("Expected a comma")
                        } else {
                            this.plainStringReader("-", tag, PlainStyleMode.FLOW_SEQUENCE, 0, this::jsonTokenCreator)
                        }
                    }
                    ',' -> {
                        if(this.state != FlowSequenceState.MAP_END && this.state != FlowSequenceState.VALUE_START) {
                            return this.jsonTokenCreator(null, false, null, 0)
                        }

                        read()
                        return tokenReturner {
                            this.readUntilToken(0)
                        }
                    }
                    ':' -> {
                        read()
                        if (this.state != FlowSequenceState.MAP_VALUE_AFTER_COMPLEX_KEY) {
                            this.state = FlowSequenceState.KEY
                        }

                        return tokenReturner {
                            this.readUntilToken(0)
                        }
                    }
                    ']' -> {
                        tokenReturner {
                            this.state = FlowSequenceState.STOP
                            read()
                            this.currentReader = this.parentReader
                            JsonToken.EndArray
                        }
                    }
                    '}' -> {
                        read() // This should be handled in Map reader. Otherwise incorrect content
                        throw InvalidYamlContent("Invalid char $lastChar at this position")
                    }
                    '?' -> {
                        read()
                        if (this.lastChar.isWhitespace()) {
                            if (stateAtStart == FlowSequenceState.EXPLICIT_KEY) {
                                throw InvalidYamlContent("Cannot have two ? explicit keys in a row")
                            }
                            this.state = FlowSequenceState.EXPLICIT_KEY
                            this.jsonTokenCreator(null, false, tag, 0)
                        } else if(this.lastChar == ',' || this.lastChar == ':') {
                            this.state = FlowSequenceState.EXPLICIT_KEY
                            this.jsonTokenCreator(null, false, null, 0)
                        } else {
                            this.plainStringReader("?", tag, PlainStyleMode.FLOW_SEQUENCE, 0, this::jsonTokenCreator)
                        }
                    }
                    '|', '>', '@', '`' -> throw InvalidYamlContent("Unsupported character $lastChar in flow array")
                    else -> this.plainStringReader("", tag, PlainStyleMode.FLOW_SEQUENCE, indentToAdd, this::jsonTokenCreator)
                }
            }
        }
    }

    override fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ) =
        throw InvalidYamlContent("Missing a comma")

    private fun checkComplexFieldAndReturn(jsonToken: JsonToken): JsonToken {
        if (this.state == FlowSequenceState.KEY) {
            this.state = FlowSequenceState.COMPLEX_KEY
            this.yamlReader.pushToken(jsonToken)
            return JsonToken.StartComplexFieldName
        }
        return jsonToken
    }

    private fun tokenReturner(doIfNoToken: () -> JsonToken): JsonToken {
        this.cachedCall?.let {
            this.cachedCall = null
            if (this.state == FlowSequenceState.VALUE_START) {
                this.state = FlowSequenceState.VALUE
            }
            return it()
        }

        return if (this.state == FlowSequenceState.MAP_END) {
            this.state = FlowSequenceState.VALUE_START
            JsonToken.EndObject
        } else if (this.state == FlowSequenceState.KEY || this.state == FlowSequenceState.MAP_VALUE) {
            this.jsonTokenCreator(null, false, null, 0)
        } else {
            doIfNoToken()
        }
    }

    private fun jsonTokenCreator(value: String?, isPlainStringReader: Boolean, tag: TokenType?, @Suppress("UNUSED_PARAMETER") extraIndentAtStart: Int): JsonToken = when(this.state) {
        FlowSequenceState.START -> throw Exception("Sequence cannot be in start mode")
        FlowSequenceState.EXPLICIT_KEY -> this.startObject(tag)
        FlowSequenceState.VALUE_START -> {
            this.cachedCall = { this.jsonTokenCreator(value, isPlainStringReader, tag, 0) }
            this.readUntilToken(0)
        }
        FlowSequenceState.KEY -> {
            this.state = FlowSequenceState.MAP_VALUE
            checkAndCreateFieldName(value, isPlainStringReader)
        }
        FlowSequenceState.COMPLEX_KEY -> {
            this.state = FlowSequenceState.MAP_VALUE_AFTER_COMPLEX_KEY
            JsonToken.EndComplexFieldName
        }
        FlowSequenceState.VALUE -> {
            this.state = FlowSequenceState.VALUE_START
            createYamlValueToken(value, tag, isPlainStringReader)
        }
        FlowSequenceState.MAP_VALUE, FlowSequenceState.MAP_VALUE_AFTER_COMPLEX_KEY -> {
            this.state = FlowSequenceState.MAP_END
            createYamlValueToken(value, tag, isPlainStringReader)
        }
        FlowSequenceState.MAP_END, FlowSequenceState.STOP -> {
            throw Exception("Not a content token creator")
        }
    }

    override fun foundMap(tag: TokenType?, startedAtIndent: Int) =
        if (this.state == FlowSequenceState.VALUE_START) {
            startObject(tag)
        } else {
            null
        }.also {
            this.state = FlowSequenceState.MAP_VALUE
        }

    private fun startObject(tag: TokenType?): JsonToken {
        return tag?.let {
            JsonToken.StartObject(
                tag as? MapType ?: throw InvalidYamlContent("$tag should be a map type")
            )
        } ?: JsonToken.SimpleStartObject
    }

    // Sequences can only return single item maps
    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        JsonToken.FieldName(fieldName)

    override fun handleReaderInterrupt(): JsonToken {
        if (this.state != FlowSequenceState.STOP) {
            throw InvalidYamlContent("Sequences started with [ should always end with a ]")
        }
        this.currentReader = this.parentReader
        return JsonToken.EndArray
    }
}

/**
 * Creates a FlowSequenceReader within a YamlCharReader with [tag] as typing.
 * Reads until first token and returns it
 */
internal fun <P> P.flowSequenceReader(tag: TokenType?, indentToAdd: Int): JsonToken
        where P : YamlCharReader,
              P : IsYamlCharWithIndentsReader {
    read()
    return FlowSequenceReader(
        yamlReader = this.yamlReader,
        parentReader = this,
        indentToAdd = indentToAdd
    ).let {
        this.currentReader = it
        it.readUntilToken(0, tag)
    }
}
