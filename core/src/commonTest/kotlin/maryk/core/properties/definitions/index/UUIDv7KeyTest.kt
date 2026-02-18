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

internal class UUIDv7KeyTest {
    object MarykModel : RootDataModel<MarykModel>(
        keyDefinition = { UUIDv7Key },
    ) {
        val value by string(1u)
    }

    @Test
    fun testKey() {
        val obj = MarykModel.create {
            value with "test"
        }

        val key = MarykModel.key(obj)
        expect(16) { key.bytes.size }

        val keyDef = MarykModel.Meta.keyDefinition

        assertIs<UUIDv7Key>(keyDef).apply {
            var index = 0
            val uuid = readStorageBytes(key.size) {
                key.bytes[index++]
            }

            uuid.toLongs { msb, _ ->
                expect(7) { ((msb ushr 12) and 0xF).toInt() }
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
        checkProtoBufConversion(UUIDv7Key, UUIDv7Key.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(UUIDv7Key, UUIDv7Key.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(UUIDv7Key, UUIDv7Key.Model)
    }

    @Test
    fun toReferenceStorageBytes() {
        expect("06") { UUIDv7Key.toReferenceStorageByteArray().toHexString() }
    }
}
