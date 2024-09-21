package maryk.yaml

import maryk.json.JsonToken

/** Records values behind an &anchor to enable to reuse them in other spots */
internal class AnchorRecorder(
    private val anchor: String
) {
    private val storedValues = mutableListOf<JsonToken>()
    private var tokenStartDepth: Int? = null

    /** Records token from yamlReader and triggers [onEnd] if back at starting depth */
    fun recordToken(token: JsonToken, tokenDepth: Int, onEnd: (String, Array<JsonToken>) -> Unit) {
        storedValues.add(token)

        if (tokenStartDepth == tokenDepth) {
            onEnd(anchor, storedValues.toTypedArray())
        }
    }

    /** Set [tokenDepth] on which this token reader started */
    fun setTokenStartDepth(tokenDepth: Int) {
        tokenStartDepth = tokenDepth
    }
}

/** Reads an anchor and fires [onDone] when done */
internal fun IsYamlCharReader.anchorReader(onDone: () -> JsonToken): JsonToken {
    val anchor = buildString {
        read() // Skip the '&' character
        while (!lastChar.isWhitespace()) {
            append(lastChar)
            read()
        }
    }

    if (anchor.isEmpty()) {
        throw InvalidYamlContent("Name of anchor (&) needs at least 1 character")
    }

    // Pass this anchor recorder to the YamlReader so it can start to pass tokens
    yamlReader.recordAnchors(AnchorRecorder(anchor))

    return onDone()
}
