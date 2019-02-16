package maryk.core.properties.definitions.key

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

internal class UUIDKeyTest {
    object MarykModel: RootDataModel<MarykModel, MarykModel.Properties>(
        name = "MarykModel",
        keyDefinition = UUIDKey,
        properties = Properties
    ) {
        object Properties : PropertyDefinitions() {
            val value = add(1, "value", StringDefinition())
        }

        operator fun invoke(
            value: String
        ) = this.values {
            Properties.mapNonNulls(
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

        uuid.first shouldBe msb
        uuid.second shouldBe lsb
    }

    @Test
    fun testKey(){
        val obj = MarykModel("test")

        val key = MarykModel.key(obj)
        key.bytes.size shouldBe 16

        val keyDef = MarykModel.keyDefinition

        (keyDef === UUIDKey) shouldBe true
        val specificDef = keyDef as UUIDKey

        var index = 0
        val uuid = specificDef.readStorageBytes(key.size) {
            key.bytes[index++]
        }

        val bc = ByteCollector()
        bc.reserve(16)
        specificDef.writeStorageBytes(uuid, bc::write)

        bc.bytes!! contentEquals key.bytes shouldBe true
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
        UUIDKey.toReferenceStorageByteArray().toHex() shouldBe "01"
    }
}
