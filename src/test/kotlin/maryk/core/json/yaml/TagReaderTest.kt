package maryk.core.json.yaml

import maryk.core.json.ArrayType
import maryk.core.json.MapType
import maryk.core.json.ValueType
import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForFieldName
import maryk.core.json.testForObjectEnd
import maryk.core.json.testForObjectStart
import maryk.core.json.testForValue
import maryk.core.properties.definitions.PropertyDefinitionType
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
        |    k5: !!str
        |      true
        |    k6:
        |      true
        |    k7: !!set
        |    - v1
        |    - v2
        |    - v3
        |    k8:
        |    - t1
        |    k9: !!set [f1, f2]
        |    k10: !!omap
        |       a1: 1
        |       a2: 2
        |    k11:
        |       b1: 1
        |    k12: !!pairs {test: value}
        """.trimMargin())

        testForObjectStart(reader)
        testForFieldName(reader, "k1")
        testForObjectStart(reader, PropertyDefinitionType.Boolean)
        testForFieldName(reader, "k")
        testForValue(reader, "v")
        testForObjectEnd(reader)
        testForFieldName(reader, "k2")
        testForValue(reader, "v2", ValueType.String)
        testForFieldName(reader, "k3")
        testForValue(reader, true, ValueType.Bool)
        testForFieldName(reader, "k4")
        testForValue(reader, "v4", ValueType.String)
        testForFieldName(reader, "k5")
        testForValue(reader, "true", ValueType.String)
        testForFieldName(reader, "k6")
        testForValue(reader, true, ValueType.Bool)
        testForFieldName(reader, "k7")
        testForArrayStart(reader, ArrayType.Set)
        testForValue(reader, "v1")
        testForValue(reader, "v2")
        testForValue(reader, "v3")
        testForArrayEnd(reader)
        testForFieldName(reader, "k8")
        testForArrayStart(reader, ArrayType.Sequence)
        testForValue(reader, "t1")
        testForArrayEnd(reader)
        testForFieldName(reader, "k9")
        testForArrayStart(reader, ArrayType.Set)
        testForValue(reader, "f1")
        testForValue(reader, "f2")
        testForArrayEnd(reader)
        testForFieldName(reader, "k10")
        testForObjectStart(reader, MapType.OrderedMap)
        testForFieldName(reader, "a1")
        testForValue(reader, 1.toLong())
        testForFieldName(reader, "a2")
        testForValue(reader, 2.toLong())
        testForObjectEnd(reader)
        testForFieldName(reader, "k11")
        testForObjectStart(reader, MapType.Map)
        testForFieldName(reader, "b1")
        testForValue(reader, 1.toLong(), ValueType.Int)
        testForObjectEnd(reader)
        testForFieldName(reader, "k12")
        testForObjectStart(reader, MapType.Pairs)
        testForFieldName(reader, "test")
        testForValue(reader, "value")
        testForObjectEnd(reader)
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
        testForObjectStart(reader, PropertyDefinitionType.Boolean)
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
        |     !test!bool true, !<tag:yaml.org,2002:str> v4, !!set [a1, a2]]
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader, PropertyDefinitionType.Boolean)
        testForFieldName(reader, "k")
        testForValue(reader, "v")
        testForObjectEnd(reader)
        testForValue(reader, "v2", ValueType.String)
        testForValue(reader, true, ValueType.Bool)
        testForValue(reader, "v4")
        testForArrayStart(reader, ArrayType.Set)
        testForValue(reader, "a1")
        testForValue(reader, "a2")
        testForArrayEnd(reader)
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
        testForObjectStart(reader, PropertyDefinitionType.Boolean)
        testForFieldName(reader, "k1")
        testForValue(reader, "v1")
        testForObjectEnd(reader)
        testForObjectStart(reader, PropertyDefinitionType.SubModel)
        testForFieldName(reader, "k2")
        testForValue(reader, "v2")
        testForObjectEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }
}