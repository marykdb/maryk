package maryk.core.json.yaml

internal fun createYamlReader(yaml: String): YamlReader {
    val input = yaml
    var index = 0

    val reader = YamlReader {
        val b = input[index].also {
            // JS platform returns a 0 control char when nothing can be read
            if (it == '\u0000') {
                throw Throwable("0 char encountered")
            }
        }
        index++
        b
    }
    return reader
}
