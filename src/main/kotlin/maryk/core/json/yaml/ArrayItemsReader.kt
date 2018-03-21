package maryk.core.json.yaml

import maryk.core.json.ArrayType
import maryk.core.json.JsonToken
import maryk.core.json.TokenType

/** Reader for Array Items */
internal class ArrayItemsReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    val indentToAdd: Int = 0,
    startTag: TokenType? = null
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var tag: ArrayType? = null
    private var isStarted = false

    init {
        startTag?.let {
            this.setTag(it)
        }
    }

    override fun readUntilToken(): JsonToken {
        return if (!this.isStarted) {
            createLineReader(this)

            this.isStarted = true
            return this.tag?.let {
                JsonToken.StartArray(it)
            } ?: JsonToken.SimpleStartArray
        } else {
            IndentReader(
                yamlReader, this
            ).let {
                this.currentReader = it
                it.readUntilToken()
            }
        }
    }

    private fun setTag(tag: TokenType?) {
        this.tag = tag?.let {
            it as? ArrayType ?: throw InvalidYamlContent("Can only use sequence tags on sequences")
        }
    }

    override fun foundMapKey(isExplicitMap: Boolean) = this.parentReader.foundMapKey(isExplicitMap)

    override fun isWithinMap() = this.parentReader.isWithinMap()

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        this.createLineReader(parentReader)
        this.setTag(tag)
        return this.currentReader.readUntilToken()
    }

    override fun continueIndentLevel(tag: TokenType?): JsonToken {
        if (this.lastChar != '-') {
            val indentCount = this.indentCount()
            if (this.parentReader.isWithinMap() && this.parentReader.indentCount() == indentCount) {
                this.yamlReader.hasUnclaimedIndenting(indentCount)
                this.parentReader.childIsDoneReading()
                return JsonToken.EndArray
            }
            throwSequenceException()
        }
        read()
        if (!this.lastChar.isWhitespace()) {
            throwSequenceException()
        }
        read()

        this.setTag(tag)
        return createLineReader(this).readUntilToken()
    }

    override fun indentCount() = this.parentReader.indentCountForChildren() + this.indentToAdd

    override fun indentCountForChildren() = this.indentCount() + 1

    override fun endIndentLevel(indentCount: Int, tokenToReturn: (() -> JsonToken)?): JsonToken {
        if (indentCount == this.indentCount()) {
            // this reader should handle the read
            this.currentReader = this
            return if (tokenToReturn != null) {
                this.yamlReader.hasUnclaimedIndenting(indentCount)
                tokenToReturn()
            } else {
                this.yamlReader.hasUnclaimedIndenting(null)
                this.continueIndentLevel(null)
            }
        }

        return if (indentToAdd > 0) {
            this.yamlReader.hasUnclaimedIndenting(indentCount)
            this.parentReader.childIsDoneReading()
            tokenToReturn?.let {
                this.yamlReader.pushToken(it())
            }
            JsonToken.EndArray
        } else {
            this.parentReader.endIndentLevel(indentCount) { JsonToken.EndArray }
        }
    }

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.currentReader = this.parentReader
        return JsonToken.EndArray
    }

    private fun throwSequenceException() {
        throw InvalidYamlContent("Sequence was started on this indentation level, this is not an Sequence entry")
    }

    private fun <P> createLineReader(parentReader: P)
            where P : maryk.core.json.yaml.YamlCharReader,
                  P : maryk.core.json.yaml.IsYamlCharWithChildrenReader,
                  P : maryk.core.json.yaml.IsYamlCharWithIndentsReader = LineReader(
        yamlReader = yamlReader,
        parentReader = parentReader
    ).apply {
        this.currentReader = this
    }
}
