package maryk.yaml

import maryk.json.JsonToken
import maryk.json.JsonToken.EndComplexFieldName
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.SimpleStartObject
import maryk.json.JsonToken.StartComplexFieldName
import maryk.json.JsonToken.StartObject
import maryk.json.MapType
import maryk.json.TokenType
import maryk.lib.exceptions.ParseException
import maryk.yaml.FlowMapState.COMPLEX_KEY
import maryk.yaml.FlowMapState.EXPLICIT_KEY
import maryk.yaml.FlowMapState.KEY
import maryk.yaml.FlowMapState.SEPARATOR
import maryk.yaml.FlowMapState.START
import maryk.yaml.FlowMapState.STOP
import maryk.yaml.FlowMapState.VALUE
import maryk.yaml.PlainStyleMode.FLOW_MAP

private enum class FlowMapState {
    START, EXPLICIT_KEY, KEY, COMPLEX_KEY, VALUE, SEPARATOR, STOP
}

/** Reader for flow Map Items {key1: value1, key2: value2} */
internal class FlowMapReader<out P: IsYamlCharWithIndentsReader>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    private val indentToAdd: Int
) : YamlCharWithParentAndIndentReader<P>(yamlReader, parentReader) {
    private var state = START
    private val fieldNames = mutableListOf<String?>()

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        val stateAtStart = this.state
        if (this.state == EXPLICIT_KEY) {
            this.state = KEY
        }

        return when (this.state) {
            START -> {
                this.state = KEY
                tag?.let { tokenType ->
                    (tokenType as? MapType)?.let {
                        StartObject(it)
                    } ?: throw InvalidYamlContent("Cannot use non map tags on maps")
                } ?: SimpleStartObject
            }
            COMPLEX_KEY -> this.jsonTokenCreator(null, false, tag, extraIndent)
            else -> {
                while (this.lastChar.isWhitespace()) {
                    read()
                }

                return when (this.lastChar) {
                    '\'' -> this.singleQuoteString(tag, extraIndent, this::jsonTokenCreator)
                    '\"' -> this.doubleQuoteString(tag, extraIndent, this::jsonTokenCreator)
                    '[' -> {
                        this.flowSequenceReader(tag, 0)
                            .let(this::checkComplexFieldAndReturn)
                    }
                    '{' -> {
                        this.flowMapReader(tag, 0)
                            .let(this::checkComplexFieldAndReturn)
                    }
                    '!' -> this.tagReader { this.readUntilToken(extraIndent, it) }
                    '&' -> this.anchorReader { this.readUntilToken(extraIndent, tag) }
                    '*' -> {
                        this.state = SEPARATOR
                        this.aliasReader(FLOW_MAP)
                    }
                    '-' -> {
                        read()
                        if (this.lastChar.isWhitespace()) {
                            throw InvalidYamlContent("Cannot have regular sequence items in a flow map")
                        } else this.plainStringReader("-", tag, FLOW_MAP, 0, this::jsonTokenCreator)
                    }
                    ',' -> {
                        if (this.state != SEPARATOR) {
                            return this.jsonTokenCreator(null, false, tag, extraIndent)
                        }

                        read()
                        this.readUntilToken(extraIndent)
                    }
                    ':' -> {
                        read()
                        this.readUntilToken(extraIndent)
                    }
                    ']' -> {
                        read() // This should be handled in Array reader. Otherwise incorrect content
                        throw InvalidYamlContent("Invalid char $lastChar at this position")
                    }
                    '}' -> {
                        if (this.state != SEPARATOR) {
                            return this.jsonTokenCreator(null, false, tag, extraIndent)
                        }
                        this.state = STOP

                        read()
                        this.currentReader = this.parentReader
                        EndObject
                    }
                    '?' -> {
                        read()
                        return if (this.lastChar.isWhitespace()) {
                            if (stateAtStart == EXPLICIT_KEY) {
                                throw InvalidYamlContent("Cannot have two ? explicit keys in a row")
                            }
                            this.state = EXPLICIT_KEY
                            this.jsonTokenCreator(null, false, tag, extraIndent)
                        } else if (this.lastChar == ',' || this.lastChar == ':') {
                            this.jsonTokenCreator(null, false, tag, extraIndent)
                        } else {
                            this.plainStringReader("?", tag, FLOW_MAP, 0, this::jsonTokenCreator)
                        }
                    }
                    '|', '>', '@', '`' -> throw InvalidYamlContent("Unsupported character $lastChar in flow map")
                    else -> this.plainStringReader(
                        "",
                        tag,
                        FLOW_MAP,
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
    ) =
        throw InvalidYamlContent("Did not close map")

    private fun jsonTokenCreator(
        value: String?,
        isPlainStringReader: Boolean,
        tag: TokenType?, @Suppress("UNUSED_PARAMETER") extraIndent: Int
    ) = when (this.state) {
        START, STOP -> throw ParseException("Map cannot create tokens in state $state")
        EXPLICIT_KEY -> this.readUntilToken(0)
        KEY, SEPARATOR -> {
            this.state = VALUE
            this.checkAndCreateFieldName(value, isPlainStringReader)
        }
        COMPLEX_KEY -> {
            this.state = VALUE
            EndComplexFieldName
        }
        VALUE -> {
            this.state = SEPARATOR

            if (value == null) {
                this.createTokensFittingTag(tag)
            } else {
                createYamlValueToken(value, tag, isPlainStringReader)
            }
        }
    }

    override fun foundMap(tag: TokenType?, startedAtIndent: Int): JsonToken? = null

    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        checkAndCreateFieldName(this.fieldNames, fieldName, isPlainStringReader)

    private fun checkComplexFieldAndReturn(jsonToken: JsonToken): JsonToken {
        if (this.state == KEY) {
            this.yamlReader.pushToken(jsonToken)
            this.state = COMPLEX_KEY
            return StartComplexFieldName
        }
        this.state = SEPARATOR
        return jsonToken
    }

    override fun handleReaderInterrupt(): JsonToken {
        if (this.state != STOP) {
            throw InvalidYamlContent("Maps started with { should always end with a }")
        }
        this.currentReader = this.parentReader
        return EndObject
    }
}

/**
 * Creates a FlowMapReader within a YamlCharReader with [tag] as typing.
 * Reads until first token and returns it
 */
internal fun <P: IsYamlCharWithIndentsReader> P.flowMapReader(tag: TokenType?, indentToAdd: Int): JsonToken {
    read()
    return FlowMapReader(
        yamlReader = this.yamlReader,
        parentReader = this,
        indentToAdd = indentToAdd
    ).let {
        this.currentReader = it
        it.readUntilToken(0, tag)
    }
}
