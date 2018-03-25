package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.MapType
import maryk.core.json.TokenType

/** Reader for Map Items */
internal class MapItemsReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    private val indentToAdd: Int = 0,
    startTag: TokenType? = null
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var tag: MapType? = null
    private var isStarted = false
    private val fieldNames = mutableListOf<String?>()

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
                JsonToken.StartObject(it)
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

    private fun setTag(tag: TokenType?) {
        this.tag = tag?.let {
            it as? MapType ?: throw InvalidYamlContent("Can only use map tags on maps")
        }
    }

    override fun foundMap(isExplicitMap: Boolean) = null

    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        checkAndCreateFieldName(this.fieldNames, fieldName, isPlainStringReader)


    override fun isWithinMap() = true

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        this.createLineReader(parentReader)
        this.setTag(tag)
        return this.currentReader.readUntilToken()
    }

    override fun continueIndentLevel(tag: TokenType?): JsonToken {
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
                this.yamlReader.setUnclaimedIndenting(indentCount)
                tokenToReturn()
            } else {
                this.yamlReader.setUnclaimedIndenting(null)
                this.continueIndentLevel(null)
            }
        }

        return if (indentToAdd > 0) {
            this.yamlReader.setUnclaimedIndenting(indentCount)
            this.parentReader.childIsDoneReading(false)
            tokenToReturn?.let {
                this.yamlReader.pushToken(JsonToken.EndObject)
                return it()
            }
            JsonToken.EndArray
        } else {
            val returnFunction = tokenToReturn?.let {
                this.yamlReader.pushToken(JsonToken.EndObject)
                it
            } ?: { JsonToken.EndObject }

            this.parentReader.endIndentLevel(indentCount, returnFunction)
        }
    }

    override fun childIsDoneReading(closeLineReader: Boolean) {
        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.currentReader = this.parentReader
        return JsonToken.EndObject
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
