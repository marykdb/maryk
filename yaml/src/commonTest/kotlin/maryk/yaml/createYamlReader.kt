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
        yaml.getOrNull(index)?.also {
            alreadyRead += it
            index++
        } ?: throw Throwable("0 char encountered")
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
        yaml.getOrNull(index)?.also {
            alreadyRead += it
            index++
        } ?: throw Throwable("0 char encountered")
    }
}
