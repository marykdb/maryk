package maryk.core.json.yaml

import maryk.core.json.IsJsonLikeReader

fun createYamlReader(yaml: String): IsJsonLikeReader {
    val input = yaml
    var index = 0

    var alreadyRead = ""

    return YamlReader {
        val b = input[index].also {
            // JS platform returns a 0 control char when nothing can be read
            if (it == '\u0000') {
                throw Throwable("0 char encountered")
            }
        }
        alreadyRead += b
        index++
        b
    }
}

internal fun createMarykYamlReader(yaml: String): IsJsonLikeReader {
    val input = yaml
    var index = 0

    var alreadyRead = ""

    return MarykYamlReader {
        val b = input[index].also {
            // JS platform returns a 0 control char when nothing can be read
            if (it == '\u0000') {
                throw Throwable("0 char encountered")
            }
        }
        alreadyRead += b
        index++
        b
    }
}
