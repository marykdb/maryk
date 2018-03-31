package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.TokenType

internal enum class PlainStyleMode {
    NORMAL, FLOW_SEQUENCE, FLOW_MAP
}

/** Plain style string reader */
internal class PlainStringReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    startWith: String,
    private val mode: PlainStyleMode = PlainStyleMode.NORMAL,
    private var jsonTokenConstructor: (String?) -> JsonToken
) : YamlCharWithParentAndIndentReader<P>(yamlReader, parentReader), IsYamlCharWithIndentsReader, IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var storedValue: String = startWith

    override fun readUntilToken(tag: TokenType?): JsonToken {
        loop@while(true) {
            when (this.lastChar) {
                '\n', '\r' -> {
                    this.storedValue = this.storedValue.trimEnd()
                    return IndentReader(this.yamlReader, this).let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                ':' -> {
                    read()
                    if (this.lastChar.isWhitespace()) {
                        this.parentReader.childIsDoneReading(false)

                        // Only override token creators with non flow maps
                        if (this.mode != PlainStyleMode.FLOW_MAP) {
                            this.jsonTokenConstructor = {
                                @Suppress("UNCHECKED_CAST")
                                (this.currentReader as P).checkAndCreateFieldName(it, true)
                            }

                            // If new map return Object Start and push new token
                            this.parentReader.foundMap(false, tag)?.let {
                                this.yamlReader.pushToken(this.createToken())
                                return it
                            }
                        }

                        // Else return specific token
                        return this.createToken()
                    }
                    this.storedValue += ":$lastChar"
                    read()
                }
                '#' -> {
                    if (this.storedValue.last() == ' ') {
                        return CommentReader(this.yamlReader, this).let {
                            this.currentReader = it
                            it.readUntilToken()
                        }
                    }

                    this.storeCharAndProceed()
                }
                else -> {
                    when(this.mode) {
                        PlainStyleMode.FLOW_SEQUENCE -> when (this.lastChar) {
                            ',', ']' -> {
                                this.parentReader.childIsDoneReading(false)
                                return createToken()
                            }
                            else -> {}
                        }
                        PlainStyleMode.FLOW_MAP -> when (this.lastChar) {
                            ',', '}' -> {
                                this.parentReader.childIsDoneReading(false)
                                return createToken()
                            }
                            else -> {}
                        }
                        else -> {}
                    }

                    this.storeCharAndProceed()
                }
            }
        }
    }

    private fun storeCharAndProceed() {
        this.storedValue += lastChar
        read()
    }

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?)
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader = this.continueIndentLevel(tag)

    override fun continueIndentLevel(tag: TokenType?): JsonToken {
        this.storedValue += ' '
        return this.readUntilToken()
    }

    override fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ): JsonToken {
        val readerIndentCount = this.indentCount()
        this.parentReader.childIsDoneReading(false)
        @Suppress("UNCHECKED_CAST")
        return when {
            indentCount == readerIndentCount -> this.createToken()
            this.mode == PlainStyleMode.FLOW_SEQUENCE -> throw InvalidYamlContent("Missing a comma")
            this.mode == PlainStyleMode.FLOW_MAP -> throw InvalidYamlContent("Did not close map")
            else -> this.parentReader.endIndentLevel(indentCount, tag) {
                this.createToken()
            }
        }
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.parentReader.childIsDoneReading(false)
        return this.createToken()
    }

    private fun createToken(): JsonToken {
        return this.jsonTokenConstructor(this.storedValue.trim())
    }
}

/**
 * Creates a plain string reader and returns first found token.
 * Set [startWith] to set first characters
 * Pass [tag] to set type on Value.
 * [flowMode] determines which characters can stop the reader
 * [jsonTokenCreator] creates the right jsonToken. Could be field name or value.
 */
internal fun <P> P.plainStringReader(
    startWith: String,
    tag: TokenType?,
    flowMode: PlainStyleMode,
    jsonTokenCreator: JsonTokenCreator
) where P : IsYamlCharWithChildrenReader,
              P : YamlCharReader,
              P : IsYamlCharWithIndentsReader =
    PlainStringReader(
        this.yamlReader,
        this,
        startWith,
        flowMode
    ) {
        jsonTokenCreator(it, true, tag)
    }.let {
        this.currentReader = it
        it.readUntilToken(tag)
    }
