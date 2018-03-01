package maryk.core.json.yaml

import maryk.core.json.testForInvalidYaml
import maryk.core.json.testForObjectValue
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
}