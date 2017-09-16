package maryk.core.properties.definitions.key

import io.kotlintest.matchers.shouldBe
import maryk.core.objects.Def
import maryk.core.objects.RootDataModel
import maryk.core.properties.ByteCollector
import maryk.core.properties.definitions.StringDefinition
import org.junit.Test

internal class UUIDKeyTest {
    private data class MarykObject(
            val value: String
    ){
        object Properties {
            val value = StringDefinition(
                    name = "value",
                    index = 0
            )
        }
        companion object: RootDataModel<MarykObject>(
            definitions = listOf(
                    Def(Properties.value, MarykObject::value)
            )
        )
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
        val uuid = specificDef.convertFromBytes(key.size, {
            key.bytes[index++]
        })

        val bc = ByteCollector()
        specificDef.convertToBytes(uuid, bc::reserve, bc::write)

        bc.bytes!! contentEquals key.bytes shouldBe true
    }
}