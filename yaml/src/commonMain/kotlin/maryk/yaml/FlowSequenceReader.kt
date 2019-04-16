package maryk.yaml

import maryk.json.ArrayType
import maryk.json.JsonToken
import maryk.json.JsonToken.SimpleStartArray
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartComplexFieldName
import maryk.json.JsonToken.StartObject
import maryk.json.MapType
import maryk.json.TokenType
import maryk.lib.exceptions.ParseException
import maryk.yaml.FlowSequenceState.COMPLEX_KEY
import maryk.yaml.FlowSequenceState.EXPLICIT_KEY
import maryk.yaml.FlowSequenceState.KEY
import maryk.yaml.FlowSequenceState.MAP_END
import maryk.yaml.FlowSequenceState.MAP_VALUE
import maryk.yaml.FlowSequenceState.MAP_VALUE_AFTER_COMPLEX_KEY
import maryk.yaml.FlowSequenceState.START
import maryk.yaml.FlowSequenceState.STOP
import maryk.yaml.FlowSequenceState.VALUE_START
import maryk.yaml.PlainStyleMode.FLOW_SEQUENCE

private enum class FlowSequenceState {
    START,
    VALUE_START,
    EXPLICIT_KEY,
    COMPLEX_KEY,
    MAP_VALUE_AFTER_COMPLEX_KEY,
    KEY,
    VALUE,
    MAP_VALUE,
    MAP_END,
    STOP
}

/** Reader for flow sequences [item1, item2, item3] */
internal class FlowSequenceReader<out P: IsYamlCharWithIndentsReader>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    private val indentToAdd: Int
) : YamlCharWithParentAndIndentReader<P>(yamlReader, parentReader) {
    private var state = START
    private var cachedCall: (() -> JsonToken)? = null

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        val stateAtStart = this.state
        if (this.state == EXPLICIT_KEY) {
            this.state = KEY
        }

        return when (this.state) {
            START -> {
                this.state = VALUE_START
                tag?.let {
                    StartArray(
                        it as? ArrayType ?: throw InvalidYamlContent("Can only use sequence tags on sequences")
                    )
                } ?: SimpleStartArray
            }
            COMPLEX_KEY -> this.jsonTokenCreator(null, false, null, 0)
            else -> {
                while (this.lastChar.isWhitespace()) {
                    read()
                }

                return when (this.lastChar) {
                    '\'' -> this.singleQuoteString(tag, extraIndent, this::jsonTokenCreator)
                    '\"' -> this.doubleQuoteString(tag, extraIndent, this::jsonTokenCreator)
                    '{' -> this.flowMapReader(tag, 0).let(this::checkComplexFieldAndReturn)
                    '[' -> this.flowSequenceReader(tag, 0).let(this::checkComplexFieldAndReturn)
                    '!' -> this.tagReader { this.readUntilToken(extraIndent, it) }
                    '&' -> this.anchorReader { this.readUntilToken(extraIndent, tag) }
                    '*' -> this.aliasReader(FLOW_SEQUENCE)
                    '-' -> {
                        read()
                        if (this.lastChar.isWhitespace()) {
                            throw InvalidYamlContent("Expected a comma")
                        } else {
                            this.plainStringReader("-", tag, FLOW_SEQUENCE, 0, this::jsonTokenCreator)
                        }
                    }
                    ',' -> {
                        if (this.state != MAP_END && this.state != VALUE_START) {
                            this.jsonTokenCreator(null, false, null, 0)
                        } else {
                            read()
                            tokenReturner(tag) {
                                this.readUntilToken(0)
                            }
                        }
                    }
                    ':' -> {
                        read()
                        if (this.state != MAP_VALUE_AFTER_COMPLEX_KEY) {
                            this.state = KEY
                        }

                        return tokenReturner(tag) {
                            this.readUntilToken(0)
                        }
                    }
                    ']' -> {
                        tokenReturner(tag) {
                            this.state = STOP
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
                            if (stateAtStart == EXPLICIT_KEY) {
                                throw InvalidYamlContent("Cannot have two ? explicit keys in a row")
                            }
                            this.state = EXPLICIT_KEY
                            this.jsonTokenCreator(null, false, tag, 0)
                        } else if (this.lastChar == ',' || this.lastChar == ':') {
                            this.state = EXPLICIT_KEY
                            this.jsonTokenCreator(null, false, null, 0)
                        } else {
                            this.plainStringReader("?", tag, FLOW_SEQUENCE, 0, this::jsonTokenCreator)
                        }
                    }
                    '|', '>', '@', '`' ->
                        throw InvalidYamlContent("Unsupported character $lastChar in flow array")
                    else -> this.plainStringReader(
                        "",
                        tag,
                        FLOW_SEQUENCE,
                        indentToAdd,
                        this::jsonTokenCreator
                    )
                }
            }
        }
    }

    override fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ) = throw InvalidYamlContent("Missing a comma")

    private fun checkComplexFieldAndReturn(jsonToken: JsonToken): JsonToken {
        if (this.state == KEY) {
            this.state = COMPLEX_KEY
            this.yamlReader.pushToken(jsonToken)
            return StartComplexFieldName
        }
        return jsonToken
    }

    private fun tokenReturner(tag: TokenType?, doIfNoToken: () -> JsonToken): JsonToken {
        this.cachedCall?.let {
            this.cachedCall = null
            if (this.state == VALUE_START) {
                this.state = FlowSequenceState.VALUE
            }
            return it()
        }

        return when (this.state) {
            MAP_END -> {
                this.state = VALUE_START
                JsonToken.EndObject
            }
            KEY, MAP_VALUE ->
                this.jsonTokenCreator(null, false, null, 0)
            VALUE_START -> {
                if (tag == null) {
                    doIfNoToken()
                } else {
                    this.createTokensFittingTag(tag)
                }
            }
            else -> doIfNoToken()
        }
    }

    private fun jsonTokenCreator(
        value: String?,
        isPlainStringReader: Boolean,
        tag: TokenType?, @Suppress("UNUSED_PARAMETER") extraIndentAtStart: Int
    ): JsonToken = when (this.state) {
        START -> throw ParseException("Sequence cannot be in start mode")
        EXPLICIT_KEY -> this.startObject(tag)
        VALUE_START -> {
            this.cachedCall = { this.jsonTokenCreator(value, isPlainStringReader, tag, 0) }
            this.readUntilToken(0)
        }
        KEY -> {
            this.state = MAP_VALUE
            checkAndCreateFieldName(value, isPlainStringReader)
        }
        COMPLEX_KEY -> {
            this.state = MAP_VALUE_AFTER_COMPLEX_KEY
            JsonToken.EndComplexFieldName
        }
        FlowSequenceState.VALUE -> {
            this.state = VALUE_START
            createYamlValueToken(value, tag, isPlainStringReader)
        }
        MAP_VALUE, MAP_VALUE_AFTER_COMPLEX_KEY -> {
            this.state = MAP_END
            createYamlValueToken(value, tag, isPlainStringReader)
        }
        MAP_END, STOP ->
            throw ParseException("Not a content token creator")
    }

    override fun foundMap(tag: TokenType?, startedAtIndent: Int) =
        if (this.state == VALUE_START) {
            startObject(tag)
        } else {
            null
        }.also {
            this.state = MAP_VALUE
        }

    private fun startObject(tag: TokenType?): JsonToken {
        return tag?.let {
            StartObject(
                tag as? MapType ?: throw InvalidYamlContent("$tag should be a map type")
            )
        } ?: JsonToken.SimpleStartObject
    }

    // Sequences can only return single item maps
    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        JsonToken.FieldName(fieldName)

    override fun handleReaderInterrupt(): JsonToken {
        if (this.state != STOP) {
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
internal fun <P: IsYamlCharWithIndentsReader> P.flowSequenceReader(tag: TokenType?, indentToAdd: Int): JsonToken {
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
