package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.json.JsonToken
import maryk.core.json.MapType
import maryk.core.json.TokenType

/** Reader for Map Items */
internal class MapItemsReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    private val isExplicitMap: Boolean,
    private val indentToAdd: Int = 0
) : YamlCharWithParentAndIndentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var isStarted = false
    private val fieldNames = mutableListOf<String?>()

    override fun readUntilToken(tag: TokenType?): JsonToken {
        return if (!this.isStarted) {
            this.lineReader(this, this.lastChar.isLineBreak(), this.isExplicitMap)

            this.isStarted = true
            return tag?.let {
                val mapType = it as? MapType ?: throw InvalidYamlContent("Can only use map tags on maps")
                JsonToken.StartObject(mapType)
            } ?: JsonToken.SimpleStartObject
        } else {
            IndentReader(
                yamlReader, this
            ).let {
                this.currentReader = it
                it.readUntilToken()
            }
        }
    }

    override fun foundMap(isExplicitMap: Boolean, tag: TokenType?): JsonToken? = null

    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        checkAndCreateFieldName(this.fieldNames, fieldName, isPlainStringReader)

    override fun isWithinMap() = true

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        @Suppress("UNCHECKED_CAST")
        (this as P).lineReader(parentReader, true, this.isExplicitMap)
        return this.currentReader.readUntilToken(tag)
    }

    override fun continueIndentLevel(tag: TokenType?): JsonToken {
        return lineReader(this, true, this.isExplicitMap).readUntilToken(tag)
    }

    override fun indentCount() = this.parentReader.indentCountForChildren() + this.indentToAdd

    override fun indentCountForChildren() = this.indentCount() + 1

    override fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ): JsonToken {
        if (indentCount == this.indentCount()) {
            // this reader should handle the read
            this.currentReader = this
            return if (tokenToReturn != null) {
                this.yamlReader.setUnclaimedIndenting(indentCount)
                tokenToReturn()
            } else {
                this.yamlReader.setUnclaimedIndenting(null)
                this.continueIndentLevel(tag)
            }
        }

        this.parentReader.childIsDoneReading(false)
        return if (indentToAdd > 0) {
            this.yamlReader.setUnclaimedIndenting(indentCount)
            tokenToReturn?.let {
                this.yamlReader.pushToken(JsonToken.EndObject)
                return it()
            }
            JsonToken.EndObject
        } else {
            val returnFunction = tokenToReturn?.let {
                this.yamlReader.pushToken(JsonToken.EndObject)
                it
            } ?: { JsonToken.EndObject }

            this.parentReader.endIndentLevel(indentCount, tag, returnFunction)
        }
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.currentReader = this.parentReader
        return JsonToken.EndObject
    }
}
