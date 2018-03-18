package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.TokenType

internal abstract class YamlTagReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    val flowMode: PlainStyleMode,
    var tag: TokenType?
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithChildrenReader,
    IsYamlCharWithIndentsReader
        where P : IsYamlCharWithChildrenReader,
              P : YamlCharReader,
              P : IsYamlCharWithIndentsReader {

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        this.tag = tag
        return this.readUntilToken()
    }

    override fun continueIndentLevel(tag: TokenType?): JsonToken {
        this.tag = tag
        return this.readUntilToken()
    }

    override fun endIndentLevel(indentCount: Int, tokenToReturn: (() -> JsonToken)?) =
        this.readUntilToken()

    override fun indentCount() = this.parentReader.indentCountForChildren()

    override fun indentCountForChildren() = this.parentReader.indentCountForChildren()

    override fun foundMapKey(isExplicitMap: Boolean) = this.parentReader.foundMapKey(isExplicitMap)

    override fun isWithinMap() = this.parentReader.isWithinMap()

    abstract fun jsonTokenCreator(value: String?, isPlainStringReader: Boolean): JsonToken

    internal fun plainStringReader(startWith: String): JsonToken {
        return PlainStringReader(
            this.yamlReader,
            this,
            startWith,
            this.flowMode
        ) {
            this.jsonTokenCreator(it, true)
        }.let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    protected fun singleQuoteString(): JsonToken {
        read()
        return StringInSingleQuoteReader(this.yamlReader, this, {
            this.jsonTokenCreator(it, false)
        }).let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    protected fun doubleQuoteString(): JsonToken {
        read()
        return StringInDoubleQuoteReader(this.yamlReader, this, {
            this.jsonTokenCreator(it, false)
        }).let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    protected fun flowSequenceReader(): JsonToken {
        read()
        return FlowSequenceReader(
            yamlReader = this.yamlReader,
            parentReader = this,
            startTag = this.tag
        ).let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    protected fun flowMapReader(): JsonToken {
        read()
        return FlowMapItemsReader(
            yamlReader = this.yamlReader,
            parentReader = this,
            startTag = this.tag
        ).let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    protected fun tagReader(): JsonToken {
        return TagReader(this.yamlReader, this).let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    override fun handleReaderInterrupt(): JsonToken {
        return this.parentReader.handleReaderInterrupt()
    }

    override fun childIsDoneReading() {
        this.currentReader = this
    }
}