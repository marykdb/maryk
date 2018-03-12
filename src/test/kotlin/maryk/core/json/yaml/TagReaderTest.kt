package maryk.core.json.yaml

import maryk.core.json.ValueType
import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForFieldName
import maryk.core.json.testForObjectEnd
import maryk.core.json.testForObjectStart
import maryk.core.json.testForValue
import kotlin.test.Test

class TagReaderTest {
    @Test
    fun readTagsInMap() {
        val reader = createMarykYamlReader("""
        |%TAG !test! tag:yaml.org,2002:
        |---
        |    k1: !Boolean { k: v }
        |    k2: !!str v2
        |    k3: !test!bool true
        |    k4: !<tag:yaml.org,2002:str> v4
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "k1")
        testForObjectStart(reader)
        testForFieldName(reader, "k")
        testForValue(reader, "v")
        testForObjectEnd(reader)
        testForFieldName(reader, "k2")
        testForValue(reader, "v2", ValueType.String)
        testForFieldName(reader, "k3")
        testForValue(reader, true, ValueType.Bool)
        testForFieldName(reader, "k4")
        testForValue(reader, "v4", ValueType.String)
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun readTagsInFlowMap() {
        val reader = createMarykYamlReader("""
        |%TAG !test! tag:yaml.org,2002:
        |---
        |   {
        |    k2: !!str v2, k3: !test!bool true,
        |    k4: !<tag:yaml.org,2002:float> 1.4665 }
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "k2")
        testForValue(reader, "v2", ValueType.String)
        testForFieldName(reader, "k3")
        testForValue(reader, true, ValueType.Bool)
        testForFieldName(reader, "k4")
        testForValue(reader, 1.4665, ValueType.Float)
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun readTagsInSequence() {
        val reader = createMarykYamlReader("""
        |%TAG !test! tag:yaml.org,2002:
        |---
        |    - !Boolean { k: v }
        |    - !!str v2
        |    - !test!bool true
        |    - !<tag:yaml.org,2002:str> v4
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "k")
        testForValue(reader, "v")
        testForObjectEnd(reader)
        testForValue(reader, "v2", ValueType.String)
        testForValue(reader, true, ValueType.Bool)
        testForValue(reader, "v4", ValueType.String)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun readTagsInFlowSequence() {
        val reader = createMarykYamlReader("""
        |%TAG !test! tag:yaml.org,2002:
        |---
        |    [ !Boolean { k: v },
        |     !!str v2,
        |     !test!bool true, !<tag:yaml.org,2002:str> v4]
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "k")
        testForValue(reader, "v")
        testForObjectEnd(reader)
        testForValue(reader, "v2", ValueType.String)
        testForValue(reader, true, ValueType.Bool)
        testForValue(reader, "v4")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun readMarykTags() {
        val reader = createMarykYamlReader("""
        |    - !Boolean { k1: v1 }
        |    - !SubModel { k2: v2 }
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "k1")
        testForValue(reader, "v1")
        testForObjectEnd(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "k2")
        testForValue(reader, "v2")
        testForObjectEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }
}