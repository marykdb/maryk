package maryk.yaml

import maryk.json.IsJsonLikeReader
import maryk.json.MapType
import maryk.json.ValueType
import maryk.yaml.TestType.Bar
import maryk.yaml.TestType.Foo
import maryk.yaml.TestType.Test
import maryk.yaml.TestType.Value

/** Indexed type of property definitions */
sealed class TestType : MapType {
    object Foo : MapType
    object Bar : MapType
    object Test : MapType
    object Value : ValueType<Any>
}

const val defaultTag = "tag:test,2018:"

fun createSimpleYamlReader(yaml: String): IsJsonLikeReader {
    var index = 0

    var alreadyRead = ""

    return YamlReader(
        allowUnknownTags = false
    ) {
        val b = yaml[index].also {
            // JS platform returns a 0 control char when nothing can be read
            if (it == '\u0000') throw Throwable("0 char encountered")
        }
        alreadyRead += b
        index++
        b
    }
}

fun createYamlReader(yaml: String): IsJsonLikeReader {
    var index = 0

    var alreadyRead = ""

    return YamlReader(
        defaultTag = defaultTag,
        tagMap = mapOf(
            defaultTag to mapOf(
                "Foo" to Foo,
                "Bar" to Bar,
                "Test" to Test,
                "Value" to Value
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
