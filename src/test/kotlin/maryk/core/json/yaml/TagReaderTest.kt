package maryk.core.json.yaml

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
        val reader = createYamlReader("""
        |%TAG !test! tag:clarkevans.com,2002:
        |---
        |    k1: !tag v1
        |    k2: !!tag v2
        |    k3: !test!type v3
        |    k4: !<tag:yaml.org,2002:str> v4
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "k1")
        testForValue(reader, "v1")
        testForFieldName(reader, "k2")
        testForValue(reader, "v2")
        testForFieldName(reader, "k3")
        testForValue(reader, "v3")
        testForFieldName(reader, "k4")
        testForValue(reader, "v4")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun readTagsInFlowMap() {
        val reader = createYamlReader("""
        |%TAG !test! tag:clarkevans.com,2002:
        |---
        |   {k1: !tag v1,
        |    k2: !!tag v2, k3: !test!type v3,
        |    k4: !<tag:yaml.org,2002:str> v4 }
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "k1")
        testForValue(reader, "v1")
        testForFieldName(reader, "k2")
        testForValue(reader, "v2")
        testForFieldName(reader, "k3")
        testForValue(reader, "v3")
        testForFieldName(reader, "k4")
        testForValue(reader, "v4")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun readTagsInSequence() {
        val reader = createYamlReader("""
        |%TAG !test! tag:clarkevans.com,2002:
        |---
        |    - !tag v1
        |    - !!tag v2
        |    - !test!type v3
        |    - !<tag:yaml.org,2002:str> v4
        """.trimMargin())
        testForArrayStart(reader)
        testForValue(reader, "v1")
        testForValue(reader, "v2")
        testForValue(reader, "v3")
        testForValue(reader, "v4")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun readTagsInFlowSequence() {
        val reader = createYamlReader("""
        |%TAG !test! tag:clarkevans.com,2002:
        |---
        |    [ !tag v1,
        |     !!tag v2,
        |     !test!type v3, !<tag:yaml.org,2002:str> v4]
        """.trimMargin())
        testForArrayStart(reader)
        testForValue(reader, "v1")
        testForValue(reader, "v2")
        testForValue(reader, "v3")
        testForValue(reader, "v4")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }
}