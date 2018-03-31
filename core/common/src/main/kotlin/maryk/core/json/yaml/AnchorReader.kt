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

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        read()

        while(!this.lastChar.isWhitespace()) {
            anchor += this.lastChar
            read()
        }

        if (anchor.trim().isEmpty()) {
            throw InvalidYamlContent("Name of anchor (&) needs at least 1 character")
        }

        // Pass this anchor reader to the YamlReader so it can start to pass tokens
        this.yamlReader.recordAnchors(this)

        this.parentReader.childIsDoneReading(false)
        return this.parentReader.continueIndentLevel(extraIndent, null)
    }

    override fun handleReaderInterrupt(): JsonToken {
        return this.parentReader.handleReaderInterrupt()
    }

    /** Records token from yamlReader and triggers [onEnd] if back at starting depth */
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

    /** Set [tokenDepth] on which this token reader started */
    fun setTokenStartDepth(tokenDepth: Int) {
        this.tokenStartDepth = tokenDepth
    }
}

/** Constructs an anchor reader within scope of YamlReader and returns first found token */
internal fun <P> P.anchorReader(extraIndent: Int): JsonToken
        where P : IsYamlCharWithChildrenReader,
              P : YamlCharReader,
              P : IsYamlCharWithIndentsReader =
    AnchorReader(this.yamlReader, this).let {
        this.currentReader = it
        it.readUntilToken(extraIndent)
    }
