package maryk.yaml

import maryk.json.ArrayType
import maryk.json.MapType
import maryk.json.ValueType
import kotlin.test.Test

class TagReaderTest {
    @Test
    fun read_tags_in_map() {
        createYamlReader("""
        |%TAG !test! tag:yaml.org,2002:
        |---
        |    k1: !Foo { k: v }
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
        |    k12: !!pairs {test: !!str true}
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("k1")
            assertStartObject(TestType.Foo)
            assertFieldName("k")
            assertValue("v")
            assertEndObject()
            assertFieldName("k2")
            assertValue("v2", ValueType.String)
            assertFieldName("k3")
            assertValue(true, ValueType.Bool)
            assertFieldName("k4")
            assertValue("v4", ValueType.String)
            assertFieldName("k5")
            assertValue("true", ValueType.String)
            assertFieldName("k6")
            assertValue(true, ValueType.Bool)
            assertFieldName("k7")
            assertStartArray(ArrayType.Set)
            assertValue("v1")
            assertValue("v2")
            assertValue("v3")
            assertEndArray()
            assertFieldName("k8")
            assertStartArray(ArrayType.Sequence)
            assertValue("t1")
            assertEndArray()
            assertFieldName("k9")
            assertStartArray(ArrayType.Set)
            assertValue("f1")
            assertValue("f2")
            assertEndArray()
            assertFieldName("k10")
            assertStartObject(MapType.OrderedMap)
            assertFieldName("a1")
            assertValue(1.toLong())
            assertFieldName("a2")
            assertValue(2.toLong())
            assertEndObject()
            assertFieldName("k11")
            assertStartObject(MapType.Map)
            assertFieldName("b1")
            assertValue(1.toLong(), ValueType.Int)
            assertEndObject()
            assertFieldName("k12")
            assertStartObject(MapType.Pairs)
            assertFieldName("test")
            assertValue("true", ValueType.String)
            assertEndObject()
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun read_tags_in_flow_map() {
        createYamlReader("""
        |%TAG !test! tag:yaml.org,2002:
        |---
        |   {
        |    k2: !!str v2, k3: !test!bool true,
        |    k4: !<tag:yaml.org,2002:float> 1.4665 }
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("k2")
            assertValue("v2", ValueType.String)
            assertFieldName("k3")
            assertValue(true, ValueType.Bool)
            assertFieldName("k4")
            assertValue(1.4665, ValueType.Float)
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun read_tags_in_sequence() {
        createYamlReader("""
        |%TAG !test! tag:yaml.org,2002:
        |---
        |    - !Bar { k: v }
        |    - !!str v2
        |    - !test!bool true
        |    - !<tag:yaml.org,2002:str> v4
        """.trimMargin()).apply {
            assertStartArray()
            assertStartObject(TestType.Bar)
            assertFieldName("k")
            assertValue("v")
            assertEndObject()
            assertValue("v2", ValueType.String)
            assertValue(true, ValueType.Bool)
            assertValue("v4", ValueType.String)
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_tags_in_flow_sequence() {
        createYamlReader("""
        |%TAG !test! tag:yaml.org,2002:
        |---
        |    [ !Test { k: v },
        |     !!str v2,
        |     !test!bool true, !<tag:yaml.org,2002:str> v4, !!set [a1, a2], !Foo s: m, !Bar ? v]
        """.trimMargin()).apply {
            assertStartArray()
            assertStartObject(TestType.Test)
            assertFieldName("k")
            assertValue("v")
            assertEndObject()
            assertValue("v2", ValueType.String)
            assertValue(true, ValueType.Bool)
            assertValue("v4")
            assertStartArray(ArrayType.Set)
            assertValue("a1")
            assertValue("a2")
            assertEndArray()
            assertStartObject(TestType.Foo)
            assertFieldName("s")
            assertValue("m")
            assertEndObject()
            assertStartObject(TestType.Bar)
            assertFieldName("v")
            assertValue(null)
            assertEndObject()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_maryk_tags() {
        createYamlReader("""
        |    - !Foo { k1: v1 }
        |    - !Bar { k2: v2 }
        """.trimMargin()).apply {
            assertStartArray()
            assertStartObject(TestType.Foo)
            assertFieldName("k1")
            assertValue("v1")
            assertEndObject()
            assertStartObject(TestType.Bar)
            assertFieldName("k2")
            assertValue("v2")
            assertEndObject()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun fail_on_unknown_tag() {
        createYamlReader("""
        |    - !Nonsense { k1: v1 }
        """.trimMargin()).apply {
            assertStartArray()
            assertInvalidYaml()
        }
    }

    @Test
    fun read_tag_as_last() {
        createYamlReader("""
        |    - !string
        """.trimMargin()).apply {
            assertStartArray()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun fail_on_faulty_tag() {
        createYamlReader("""
        |    - !string!wrong!
        """.trimMargin()).apply {
            assertStartArray()
            assertInvalidYaml()
        }
    }
}
