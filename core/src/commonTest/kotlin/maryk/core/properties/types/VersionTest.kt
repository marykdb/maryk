package maryk.core.properties.types

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.json.InvalidJsonContent
import maryk.lib.exceptions.ParseException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class VersionTest {
    private val version = Version(1, 2, 3)

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.version, Version)
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            "1.2.3"
            """.trimIndent()
        ) {
            checkJsonConversion(this.version, Version)
        }

        expect(
            """
            "1.2"
            """.trimIndent()
        ) {
            checkJsonConversion(Version(1, 2), Version)
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            1.2.3
            """.trimIndent()
        ) {
            checkYamlConversion(this.version, Version)
        }

        expect(
            """
            3.4
            """.trimIndent()
        ) {
            checkYamlConversion(Version(3, 4), Version)
        }
    }

    @Test
    fun failOnInvalidJson() {
        assertFailsWith<ParseException> {
            Version.Model.readJson(""""1.2.3.4.5"""")
        }

        assertFailsWith<ParseException> {
            Version.Model.readJson(""""Something dirty"""")
        }

        assertFailsWith<InvalidJsonContent> {
            Version.Model.readJson("1.2.3.4")
        }
    }
}
