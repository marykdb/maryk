package maryk.yaml

import maryk.json.JsonToken
import maryk.lib.extensions.isLineBreak

/** Reads a comment and fires [onDone] when done */
internal fun YamlCharReader.commentReader(onDone: () -> JsonToken): JsonToken {
    while (!this.lastChar.isLineBreak()) {
        read()
    }
    return onDone()
}
