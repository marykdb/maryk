package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.MapType
import maryk.core.json.TokenType

/** Reads indents on a new line until a char is found */
internal class IndentReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    private var startTag: TokenType? = null
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : maryk.core.json.yaml.YamlCharReader,
              P : maryk.core.json.yaml.IsYamlCharWithChildrenReader,
              P : maryk.core.json.yaml.IsYamlCharWithIndentsReader
{
    private var indentCounter = -1
    private var mapKeyFound: Boolean = false

    // Should not be called
    override fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader =
        this.parentReader.newIndentLevel(indentCount, parentReader, tag)

    override fun continueIndentLevel(tag: TokenType?) =
        LineReader(
            this.yamlReader,
            this,
            startTag = tag
        ).let {
            this.currentReader = it
            it.readUntilToken()
        }

    override fun foundMapKey(isExplicitMap: Boolean): JsonToken? =
        if (!this.mapKeyFound) {
            this.mapKeyFound = true
            this.startTag?.let {
                this.startTag = null
                (it as? MapType)?.let {
                    JsonToken.StartObject(it)
                } ?: throw InvalidYamlContent("Cannot use non map tags on maps")
            } ?: JsonToken.SimpleStartObject
        } else {
            null
        }

    override fun isWithinMap() = this.mapKeyFound

    override fun endIndentLevel(indentCount: Int, tokenToReturn: (() -> JsonToken)?): JsonToken {
        if (this.mapKeyFound) {
            this.yamlReader.hasUnclaimedIndenting(indentCount)
            this.mapKeyFound = false

            tokenToReturn?.let {
                this.yamlReader.hasUnclaimedIndenting(indentCount)
                this.yamlReader.pushToken(it())
            }
            return JsonToken.EndObject
        }

        this.parentReader.childIsDoneReading()

        tokenToReturn?.let {
            this.yamlReader.hasUnclaimedIndenting(indentCount)
            return it()
        }

        @Suppress("UNCHECKED_CAST")
        (this.currentReader as P).let {
            return if (it.indentCount() == indentCount) {
                // found right level so continue
                this.yamlReader.hasUnclaimedIndenting(null)
                if (it is IndentReader<*>) {
                    it.continueIndentLevel(null)
                } else {
                    it.readUntilToken()
                }
            } else {
                it.endIndentLevel(indentCount, null)
            }
        }
    }

    override fun readUntilToken(): JsonToken {
        val currentIndentCount = this.yamlReader.skipEmptyLinesAndCommentsAndCountIndents()

        if (this.indentCounter == -1) {
            this.indentCounter = currentIndentCount

            if (this.parentReader is DocumentReader) {
                this.parentReader.setIndent(this.indentCounter)
            }
        }

        val parentIndentCount = this.parentReader.indentCount()
        return when(currentIndentCount) {
            parentIndentCount -> this.parentReader.continueIndentLevel(this.startTag)
            in 0 until parentIndentCount -> {
                this.parentReader.childIsDoneReading()

                val tokenToReturn: (() -> JsonToken)? = if (this.mapKeyFound) {
                    this.mapKeyFound = false
                    { JsonToken.EndObject }
                } else {
                    null
                }

                this.parentReader.endIndentLevel(currentIndentCount, tokenToReturn)
            }
            else -> if (currentIndentCount == this.indentCounter){
                this.parentReader.newIndentLevel(currentIndentCount, this, this.startTag)
            } else {
                throw InvalidYamlContent("Cannot have a new indent level which is lower than current")
            }
        }
    }

    override fun indentCount() = this.indentCounter

    override fun indentCountForChildren() = this.indentCount()

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        if (this.mapKeyFound) {
            this.mapKeyFound = false
            return JsonToken.EndObject
        }
        return parentReader.handleReaderInterrupt()
    }
}
