package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.TokenType

/** Reads an &anchor to enable to reuse them in other spots */
internal class AnchorReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P
) : YamlCharWithParentReader<P>(yamlReader, parentReader)
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var anchor = ""
    private var storedValues = mutableListOf<JsonToken>()
    private var tokenStartDepth: Int? = null

    override fun readUntilToken(tag: TokenType?): JsonToken {
        read()

        while(!this.lastChar.isWhitespace()) {
            anchor += this.lastChar
            read()
        }

        if (anchor.trim().isEmpty()) {
            throw InvalidYamlContent("Name of anchor (&) needs at least 1 character")
        }

        this.yamlReader.recordAnchors(this)

        this.parentReader.childIsDoneReading(false)
        return this.parentReader.continueIndentLevel(null)
    }

    override fun handleReaderInterrupt(): JsonToken {
        return this.parentReader.handleReaderInterrupt()
    }

    /** Records token and triggers [onEnd] if done */
    fun recordToken(token: JsonToken, tokenDepth: Int, onEnd: () -> Unit) {
        this.storedValues.add(token)

        if (this.tokenStartDepth == tokenDepth) {
            this.yamlReader.storeTokensForAnchor(
                this.anchor,
                this.storedValues.toTypedArray()
            )
            onEnd()
        }
    }

    fun setTokenDepth(tokenDepth: Int) {
        this.tokenStartDepth = tokenDepth
    }
}
