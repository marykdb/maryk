package maryk.yaml

import maryk.json.IsJsonLikeReader
import maryk.json.MapType

/** Indexed type of property definitions */
sealed class TestType: MapType {
    object Foo: MapType
    object Bar: MapType
    object Test: MapType
}

const val defaultTag = "tag:test,2018:"

fun createYamlReader(yaml: String): IsJsonLikeReader {
    var index = 0

    var alreadyRead = ""

    return YamlReader(
        defaultTag = defaultTag,
        tagMap = mapOf(
            defaultTag to mapOf(
                "Foo" to TestType.Foo,
                "Bar" to TestType.Bar,
                "Test" to TestType.Test
            )
        ),
        allowUnknownTags = false
    ) {
        val b = yaml[index].also {
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
