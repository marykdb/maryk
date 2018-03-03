package maryk.core.json.yaml

import maryk.core.json.testForInvalidYaml
import maryk.core.json.testForObjectValue
import maryk.test.shouldBe
import kotlin.test.Test

class DirectiveReaderTest {
    @Test
    fun read_yaml_directive() {
        val reader = createYamlReader("""
        |%YAML 1.2
        |---
        |test
        """.trimMargin())
        testForObjectValue(reader, "test")
    }

    @Test
    fun fail_unsupported_yaml_version() {
        val reader = createYamlReader("""
        |%YAML 2.0
        |---
        """.trimMargin())
        testForInvalidYaml(reader)
    }

    @Test
    fun fail_when_yaml_version_twice() {
        val reader = createYamlReader("""
        |%YAML 1.2
        |%YAML 1.3
        |---
        """.trimMargin())
        testForInvalidYaml(reader)
    }

    @Test
    fun ignore_unknown_directive() {
        val reader = createYamlReader("""
        |%UNKNOWN directive
        |%UNKNOWN two
        |---
        |test
        """.trimMargin())
        testForObjectValue(reader, "test")
    }

    @Test
    fun read_tags() {
        val reader = createYamlReader("""
        |%TAG ! tag:maryk.io,2018:
        |%TAG !! tag:maryk.io,2016:
        |%TAG !yaml! tag:yaml.org,2002
        |%TAG !prefix! !my-
        |%TAG !ignored!
        |---
        |test
        """.trimMargin()) as YamlReaderImpl

        testForObjectValue(reader, "test")

        reader.tags["!"] shouldBe "tag:maryk.io,2018:"
        reader.tags["!!"] shouldBe "tag:maryk.io,2016:"
        reader.tags["!yaml!"] shouldBe "tag:yaml.org,2002"
        reader.tags["!prefix!"] shouldBe "!my-"
        reader.tags["ignored"] shouldBe null
    }
}