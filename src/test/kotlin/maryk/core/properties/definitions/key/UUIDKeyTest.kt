package maryk.core.properties.definitions.key

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.ByteCollector
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.test.shouldBe
import kotlin.test.Test

internal class UUIDKeyTest {
    private data class MarykObject(
            val value: String
    ){
        object Properties : PropertyDefinitions<MarykObject>() {
            init {
                add(0, "value", StringDefinition(), MarykObject::value)
            }
        }
        companion object: RootDataModel<MarykObject, Properties>(
                name = "MarykObject",
                properties = Properties
        ) {
            override fun invoke(map: Map<Int, *>) = MarykObject(
                    map[0] as String
            )
        }
    }

    @Test
    fun testKey(){
        val obj = MarykObject("test")

        val key = MarykObject.key.getKey(obj)
        key.bytes.size shouldBe 16

        val keyDef = MarykObject.key.keyDefinitions[0]

        (keyDef is UUIDKey) shouldBe true
        val specificDef = keyDef as UUIDKey

        var index = 0
        val uuid = specificDef.readStorageBytes(key.size, {
            key.bytes[index++]
        })

        val bc = ByteCollector()
        bc.reserve(16)
        specificDef.writeStorageBytes(uuid, bc::write)

        bc.bytes!! contentEquals key.bytes shouldBe true
    }

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(UUIDKey, UUIDKey.Model)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(UUIDKey, UUIDKey.Model)
    }
}