package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.TokenType

internal abstract class YamlTagReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    val flowMode: PlainStyleMode
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
        return this.readUntilToken(tag)
    }

    override fun continueIndentLevel(tag: TokenType?): JsonToken {
        return this.readUntilToken(tag)
    }

    override fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ) =
        this.readUntilToken(tag)

    override fun indentCount() = this.parentReader.indentCountForChildren()

    override fun indentCountForChildren() = this.parentReader.indentCountForChildren()

    override fun foundMap(isExplicitMap: Boolean, tag: TokenType?) = this.parentReader.foundMap(isExplicitMap, tag)

    @Suppress("UNCHECKED_CAST")
    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        this.parentReader.checkAndCreateFieldName(
            fieldName,
            isPlainStringReader
        )

    override fun isWithinMap() = this.parentReader.isWithinMap()

    abstract fun jsonTokenCreator(value: String?, isPlainStringReader: Boolean, tag: TokenType?): JsonToken

    internal fun plainStringReader(startWith: String, tag: TokenType?): JsonToken {
        return PlainStringReader(
            this.yamlReader,
            this,
            startWith,
            this.flowMode
        ) {
            this.jsonTokenCreator(it, true, tag)
        }.let {
            this.currentReader = it
            it.readUntilToken(tag)
        }
    }

    protected fun singleQuoteString(tag: TokenType?): JsonToken {
        read()
        return StringInSingleQuoteReader(this.yamlReader, this, {
            this.jsonTokenCreator(it, false, tag)
        }).let {
            this.currentReader = it
            it.readUntilToken(tag)
        }
    }

    protected fun doubleQuoteString(tag: TokenType?): JsonToken {
        read()
        return StringInDoubleQuoteReader(this.yamlReader, this, {
            this.jsonTokenCreator(it, false, tag)
        }).let {
            this.currentReader = it
            it.readUntilToken(tag)
        }
    }

    protected fun flowSequenceReader(tag: TokenType?): JsonToken {
        read()
        return FlowSequenceReader(
            yamlReader = this.yamlReader,
            parentReader = this
        ).let {
            this.currentReader = it
            it.readUntilToken(tag)
        }
    }

    protected fun flowMapReader(tag: TokenType?): JsonToken {
        read()
        return FlowMapItemsReader(
            yamlReader = this.yamlReader,
            parentReader = this
        ).let {
            this.currentReader = it
            it.readUntilToken(tag)
        }
    }

    protected fun tagReader(): JsonToken {
        return TagReader(this.yamlReader, this).let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    protected fun anchorReader(): AnchorReader<YamlTagReader<P>> {
        return AnchorReader(this.yamlReader, this)
    }

    protected fun aliasReader(): JsonToken {
        return AliasReader(this.yamlReader, this).let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    override fun handleReaderInterrupt(): JsonToken {
        return this.parentReader.handleReaderInterrupt()
    }

    override fun childIsDoneReading(closeLineReader: Boolean) {
        this.currentReader = this
    }
}
