package maryk.core.json.yaml

import maryk.core.json.assertInvalidYaml
import maryk.core.json.assertValue
import maryk.test.shouldBe
import kotlin.test.Test

class DirectiveReaderTest {
    @Test
    fun read_yaml_directive() {
        createYamlReader("""
        |%YAML 1.2
        |---
        |test
        """.trimMargin()).apply {
            assertValue("test")
        }
    }

    @Test
    fun fail_unsupported_yaml_version() {
        createYamlReader("""
        |%YAML 2.0
        |---
        """.trimMargin()).apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_when_yaml_version_twice() {
        createYamlReader("""
        |%YAML 1.2
        |%YAML 1.3
        |---
        """.trimMargin()).apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun ignore_unknown_directive() {
        createYamlReader("""
        |%UNKNOWN directive
        |%UNKNOWN two
        |---
        |test
        """.trimMargin()).apply {
            assertValue("test")
        }
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

        reader.apply {
            assertValue("test")

            tags["!"] shouldBe "tag:maryk.io,2018:"
            tags["!!"] shouldBe "tag:maryk.io,2016:"
            tags["!yaml!"] shouldBe "tag:yaml.org,2002"
            tags["!prefix!"] shouldBe "!my-"
            tags["ignored"] shouldBe null
        }
    }
}