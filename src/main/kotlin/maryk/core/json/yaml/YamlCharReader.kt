package maryk.core.json.yaml

import maryk.core.json.JsonToken

internal abstract class YamlCharReader(
    internal val yamlReader: YamlReader,
    internal val parentReader: YamlCharWithChildrenReader? = null
) {
    val lastChar get() = this.yamlReader.lastChar

    var currentReader: YamlCharReader
        get() {
            return this.yamlReader.currentReader
        }
        set(reader) {
            this.yamlReader.currentReader = reader
        }

    fun read() = this.yamlReader.read()

    abstract fun readUntilToken(): JsonToken
    abstract fun handleReaderInterrupt(): JsonToken
}