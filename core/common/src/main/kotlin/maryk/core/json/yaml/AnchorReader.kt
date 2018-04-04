package maryk.core.json.yaml

import maryk.core.json.JsonToken

/** Records values behind an &anchor to enable to reuse them in other spots */
internal class AnchorRecorder(
    private val anchor: String
) {
    private var storedValues = mutableListOf<JsonToken>()
    private var tokenStartDepth: Int? = null

    /** Records token from yamlReader and triggers [onEnd] if back at starting depth */
    fun recordToken(token: JsonToken, tokenDepth: Int, onEnd: (String, Array<JsonToken>) -> Unit) {
        this.storedValues.add(token)

        if (this.tokenStartDepth == tokenDepth) {
            onEnd(
                this.anchor,
                this.storedValues.toTypedArray()
            )
        }
    }

    /** Set [tokenDepth] on which this token reader started */
    fun setTokenStartDepth(tokenDepth: Int) {
        this.tokenStartDepth = tokenDepth
    }
}

/** Reads an anchor and fires [onDone] when done */
internal fun YamlCharReader.anchorReader(onDone: () -> JsonToken): JsonToken {
    var anchor = ""
    read()

    while(!this.lastChar.isWhitespace()) {
        anchor += this.lastChar
        read()
    }

    if (anchor.trim().isEmpty()) {
        throw InvalidYamlContent("Name of anchor (&) needs at least 1 character")
    }

    // Pass this anchor reader to the YamlReader so it can start to pass tokens
    this.yamlReader.recordAnchors(
        AnchorRecorder(anchor)
    )

    return onDone()
}
