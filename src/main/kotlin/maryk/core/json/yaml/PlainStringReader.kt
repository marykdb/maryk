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
//    val mode: PlainStyleMode = PlainStyleMode.NORMAL,
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
            when (lastChar) {
                '\n' -> {
                    this.storedValue = this.storedValue.trimEnd()
                    return IndentReader(this.yamlReader, this).let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                ':' -> {
                    read()
                    if (lastChar.isWhitespace()) {
                        this.isDone = true
                        this.jsonTokenConstructor = { JsonToken.FieldName(it) }

                        // If new map return New Map
                        this.parentReader.foundIndentType(IndentObjectType.OBJECT)?.let {
                            return it
                        }

                        // Else return specific token
                        this.parentReader.childIsDoneReading()
                        return this.jsonTokenConstructor(storedValue)
                    }
                    this.storedValue += ":$lastChar"
                    read()
                }
                else -> {
                    this.storedValue += lastChar
                    read()
                }
            }
        }
    }

    override fun foundIndentType(type: IndentObjectType) = this.parentReader.foundIndentType(type)

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

    override fun endIndentLevel(indentCount: Int, tokenToReturn: JsonToken?): JsonToken {
        val token = this.closeReaderAndReturnValue()
        return if (indentCount == this.indentCount()) {
            token
        } else {
            this.parentReader.endIndentLevel(indentCount, token)
        }
    }

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt() = this.closeReaderAndReturnValue()

    private fun closeReaderAndReturnValue(): JsonToken {
        this.parentReader.childIsDoneReading()
        this.currentReader.let {
            if (it is LineReader<*>) {
                this.currentReader = it.parentReader
            }
        }
        return this.jsonTokenConstructor(storedValue)
    }
}