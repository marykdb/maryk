package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.json.JsonToken

/** Reads a comment and fires [onDone] when done */
internal fun YamlCharReader.commentReader(onDone: () -> JsonToken): JsonToken {
    while(!this.lastChar.isLineBreak()) {
        read()
    }
    return onDone()
}
