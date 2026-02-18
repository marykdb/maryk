@file:OptIn(ExperimentalUuidApi::class)
package maryk.core.properties.definitions.index

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.string
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.expect
import kotlin.uuid.ExperimentalUuidApi

internal class UUIDv4KeyTest {
    object MarykModel : RootDataModel<MarykModel>(
        keyDefinition = { UUIDv4Key },
    ) {
        val value by string(1u)
    }

    @Test
    fun testConversion() {
        val msb = 1039590204813653973
        val lsb = 8429492950547628920

        val b = "8e6d5dc885e4b7d5f4fb932d5a0d0378".hexToByteArray()

        val keyDef = MarykModel.Meta.keyDefinition as UUIDv4Key

        var i = 0
        val uuid = keyDef.readStorageBytes(16) {
            b[i++]
        }

        uuid.toLongs { first, second ->
            expect(msb) { first }
            expect(lsb) { second }
        }
    }

    @Test
    fun testKey() {
        val obj = MarykModel.create {
            value with "test"
        }

        val key = MarykModel.key(obj)
        expect(16) { key.bytes.size }

        val keyDef = MarykModel.Meta.keyDefinition

        assertIs<UUIDv4Key>(keyDef).apply {
            var index = 0
            val uuid = readStorageBytes(key.size) {
                key.bytes[index++]
            }

            val bc = ByteCollector()
            bc.reserve(16)
            writeStorageBytes(uuid, bc::write)

            assertTrue {
                bc.bytes!! contentEquals key.bytes
            }
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(UUIDv4Key, UUIDv4Key.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(UUIDv4Key, UUIDv4Key.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(UUIDv4Key, UUIDv4Key.Model)
    }

    @Test
    fun toReferenceStorageBytes() {
        expect("01") { UUIDv4Key.toReferenceStorageByteArray().toHexString() }
    }
}
