package maryk.yaml

import kotlin.test.Test
import kotlin.test.expect

class DirectiveReaderTest {
    @Test
    fun readYamlDirective() {
        createYamlReader("""
        |%YAML 1.2
        |---
        |test
        """.trimMargin()).apply {
            assertValue("test")
        }
    }

    @Test
    fun failUnsupportedYamlVersion() {
        createYamlReader("""
        |%YAML 2.0
        |---
        """.trimMargin()).apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun failWhenYamlVersionTwice() {
        createYamlReader("""
        |%YAML 1.2
        |%YAML 1.3
        |---
        """.trimMargin()).apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun ignoreUnknownDirective() {
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
    fun readTags() {
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

            expect("tag:maryk.io,2018:") { tags["!"] }
            expect("tag:maryk.io,2016:") { tags["!!"] }
            expect("tag:yaml.org,2002") { tags["!yaml!"] }
            expect("!my-") { tags["!prefix!"] }
            expect(null) { tags["ignored"] }
        }
    }

    @Test
    fun failOnDuplicateTag() {
        val reader = createYamlReader("""
        |%TAG !yaml! tag:yaml.org,2002
        |%TAG !yaml! tag:anotheryaml.org,2018
        |---
        |test
        """.trimMargin()) as YamlReaderImpl

        reader.apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun readOnlyTagDirective() {
        createYamlReader("""
        |%TAG !yaml! tag:yaml.org,2002
        """.trimMargin()).apply {
            assertEndDocument()
        }
    }
}
