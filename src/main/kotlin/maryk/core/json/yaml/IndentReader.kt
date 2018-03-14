package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.json.JsonToken
import maryk.core.json.MapType
import maryk.core.json.TokenType

/** Reads indents on a new line until a char is found */
internal class IndentReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    private var givenTag: TokenType? = null
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
            givenTag = tag
        ).let {
            this.currentReader = it
            it.readUntilToken()
        }

    override fun foundMapKey(isExplicitMap: Boolean): JsonToken? =
        if (!this.mapKeyFound) {
            this.mapKeyFound = true
            this.givenTag?.let {
                this.givenTag = null
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
            return JsonToken.EndObject
        }

        this.parentReader.childIsDoneReading()

        return if(tokenToReturn != null) {
            this.yamlReader.hasUnclaimedIndenting(indentCount)
            tokenToReturn()
        } else {
            @Suppress("UNCHECKED_CAST")
            (this.currentReader as P).let {
                if (it.indentCount() == indentCount) {
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
    }

    override fun readUntilToken(): JsonToken {
        var currentIndentCount = 0
        while(this.lastChar.isWhitespace()) {
            if (this.lastChar.isLineBreak()) {
                currentIndentCount = 0
            } else {
                currentIndentCount++
            }
            read()

            if (this.lastChar == '#' && currentIndentCount != 0) {
                while (!this.lastChar.isLineBreak()) {
                    read()
                }
            }
        }

        if (this.indentCounter == -1) {
            this.indentCounter = currentIndentCount
        }

        val parentIndentCount = this.parentReader.indentCount()
        return when(currentIndentCount) {
            parentIndentCount -> this.parentReader.continueIndentLevel(this.givenTag)
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
                this.parentReader.newIndentLevel(currentIndentCount, this, this.givenTag)
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