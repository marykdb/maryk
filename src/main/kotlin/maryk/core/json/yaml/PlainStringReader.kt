package maryk.core.json.yaml

import maryk.core.json.JsonToken

internal enum class PlainStyleMode {
    NORMAL, FLOW_COLLECTION, FLOW_MAP
}

/** Plain style string reader */
internal class PlainStringReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    startWith: String = "",
    private val mode: PlainStyleMode = PlainStyleMode.NORMAL,
    private var jsonTokenConstructor: (String?) -> JsonToken
) : YamlCharWithParentReader<P>(yamlReader, parentReader), IsYamlCharWithIndentsReader, IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var storedValue: String = startWith
    private var isDone = false

    override fun readUntilToken(): JsonToken {
        if (this.isDone) {
            this.parentReader.childIsDoneReading()
            return this.createToken()
        }

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
                        this.isDone = true

                        // Only override token creators with non flow maps
                        if (this.mode != PlainStyleMode.FLOW_MAP) {
                            this.jsonTokenConstructor = { JsonToken.FieldName(it) }

                            // If new map return New Map
                            this.parentReader.foundMapKey(false)?.let {
                                return it
                            }
                        }

                        // Else return specific token
                        this.parentReader.childIsDoneReading()
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
                    } else {
                        this.storeCharAndProceed()
                    }
                }
                else -> {
                    if (this.mode == PlainStyleMode.FLOW_COLLECTION) {
                        when (this.lastChar) {
                            ',', ']' -> {
                                this.isDone = true
                                this.parentReader.childIsDoneReading()
                                return createToken()
                            }
                            else -> {}
                        }
                    } else if (this.mode == PlainStyleMode.FLOW_MAP) {
                        when (this.lastChar) {
                            ',', '}' -> {
                                this.isDone = true
                                this.parentReader.childIsDoneReading()
                                return createToken()
                            }
                            else -> {}
                        }
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

    override fun foundMapKey(isExplicitMap: Boolean) = this.parentReader.foundMapKey(isExplicitMap)

    override fun indentCount() = this.parentReader.indentCountForChildren()

    override fun indentCountForChildren() = this.indentCount()

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P)
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader = this.continueIndentLevel()

    override fun continueIndentLevel(): JsonToken {
        this.storedValue += ' '
        return this.readUntilToken()
    }

    override fun endIndentLevel(indentCount: Int, tokenToReturn: (() -> JsonToken)?): JsonToken {
        val readerIndentCount = this.indentCount()
        this.setToParent()
        return when {
            indentCount == readerIndentCount -> this.createToken()
            this.mode == PlainStyleMode.FLOW_COLLECTION -> throw InvalidYamlContent("Missing a comma")
            this.mode == PlainStyleMode.FLOW_MAP -> throw InvalidYamlContent("Did not close map")
            else -> this.parentReader.endIndentLevel(indentCount) {
                this.createToken()
            }
        }
    }

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.setToParent()
        return this.createToken()
    }

    private fun setToParent() {
        this.parentReader.childIsDoneReading()
        this.currentReader.let {
            if (it is LineReader<*>) {
                (it.parentReader as IsYamlCharWithChildrenReader).childIsDoneReading()
            }
        }
    }

    private fun createToken() = this.jsonTokenConstructor(this.storedValue.trim())
}