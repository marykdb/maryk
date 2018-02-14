package maryk.core.json.yaml

import maryk.core.json.JsonToken

internal enum class PlainStyleMode {
    NORMAL, FLOW_COLLECTION
}

/** Plain style string reader */
internal class PlainStringReader<out P>(
    yamlReader: YamlReader,
    parentReader: P,
//    val mode: PlainStyleMode = PlainStyleMode.NORMAL,
    private val jsonTokenConstructor: (String?) -> JsonToken
) : YamlCharWithParentReader<P>(yamlReader, parentReader)
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader
{
    private var storedValue: String? = ""

    override fun readUntilToken(): JsonToken {
        loop@while(lastChar != '\n') {
            this.storedValue += lastChar
            read()
        }

        this.parentReader.childIsDoneReading()

        return this.jsonTokenConstructor(storedValue)
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.parentReader.childIsDoneReading()
        this.currentReader.let {
            if (it is LineReader<*>) {
                this.currentReader = it.parentReader
            }
        }
        return this.jsonTokenConstructor(storedValue)
    }
}