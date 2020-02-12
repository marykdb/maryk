package maryk.core.properties.definitions.index

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.string
import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.assertType
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.expect

internal class UUIDKeyTest {
    object MarykModel : RootDataModel<MarykModel, MarykModel.Properties>(
        keyDefinition = UUIDKey,
        properties = Properties
    ) {
        object Properties : PropertyDefinitions() {
            val value by string(1u)
        }

        operator fun invoke(
            value: String
        ) = this.values {
            mapNonNulls(
                this.value with value
            )
        }
    }

    @Test
    fun testConversion() {
        val msb = 1039590204813653973
        val lsb = 8429492950547628920

        val b = initByteArrayByHex("8e6d5dc885e4b7d5f4fb932d5a0d0378")

        val keyDef = MarykModel.keyDefinition as UUIDKey

        var i = 0
        val uuid = keyDef.readStorageBytes(16) {
            b[i++]
        }

        expect(msb) { uuid.first }
        expect(lsb) { uuid.second }
    }

    @Test
    fun testKey() {
        val obj = MarykModel("test")

        val key = MarykModel.key(obj)
        expect(16) { key.bytes.size }

        val keyDef = MarykModel.keyDefinition

        assertType<UUIDKey>(keyDef).apply {
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
        checkProtoBufConversion(UUIDKey, UUIDKey.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(UUIDKey, UUIDKey.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(UUIDKey, UUIDKey.Model)
    }

    @Test
    fun toReferenceStorageBytes() {
        expect("01") { UUIDKey.toReferenceStorageByteArray().toHex() }
    }
}
