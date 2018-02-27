package maryk.core.json.yaml

import maryk.core.json.JsonToken

internal enum class PlainStyleMode {
    NORMAL, FLOW_COLLECTION
}

/** Plain style string reader */
internal class PlainStringReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    startWith: String = "",
    val mode: PlainStyleMode = PlainStyleMode.NORMAL,
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
            return this.jsonTokenConstructor(storedValue)
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
                        this.jsonTokenConstructor = { JsonToken.FieldName(it) }

                        // If new map return New Map
                        this.parentReader.foundMapKey(false)?.let {
                            return it
                        }

                        // Else return specific token
                        this.parentReader.childIsDoneReading()
                        return this.jsonTokenConstructor(storedValue)
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
                    if (mode == PlainStyleMode.FLOW_COLLECTION) {
                        when (this.lastChar) {
                            ',', ']' -> {
                                this.isDone = true
                                this.parentReader.childIsDoneReading()
                                return this.jsonTokenConstructor(this.storedValue)
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

    override fun <P> newIndentLevel(parentReader: P)
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
        return if (indentCount == readerIndentCount) {
            this.createJsonToken()
        } else {
            this.parentReader.endIndentLevel(indentCount) {
                this.createJsonToken()
            }
        }
    }

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.setToParent()
        return this.createJsonToken()
    }

    private fun setToParent() {
        this.parentReader.childIsDoneReading()
        this.currentReader.let {
            if (it is LineReader<*>) {
                (it.parentReader as IsYamlCharWithChildrenReader).childIsDoneReading()
            }
        }
    }

    private fun createJsonToken() = this.jsonTokenConstructor(storedValue.trim())
}