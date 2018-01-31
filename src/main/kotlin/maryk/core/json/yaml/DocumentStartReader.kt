package maryk.core.json.yaml

import maryk.core.json.InvalidJsonContent
import maryk.core.json.JsonToken

internal class DocumentStartReader(
    yamlReader: YamlReader
): YamlCharReader(yamlReader) {
    override fun readUntilToken(): JsonToken {
        this.read()
        return when(this.lastChar) {
            '\'' -> {
                StringInSingleQuoteReader(this.yamlReader, this) {
                    JsonToken.ObjectValue(it)
                }.let {
                    this.yamlReader.currentReader = it
                    it.readUntilToken()
                }
            }
            '\"' -> {
                StringInDoubleQuoteReader(this.yamlReader, this) {
                    JsonToken.ObjectValue(it)
                }.let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            else -> {
                throw InvalidJsonContent("Unknown character found")
            }
        }
    }

    override fun handleReaderInterrupt() = EndReader(
        this.yamlReader
    ).apply {
        this.currentReader = this
    }.readUntilToken()
}