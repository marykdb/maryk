package maryk.core.objects

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import maryk.Option
import maryk.TestMarykObject
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.numeric.toUInt32
import org.junit.Test

internal class RootDataModelTest {
    private data class MarykObject(
            val string: String
    ){
        object Properties {
            val string = StringDefinition(
                    name = "string",
                    index = 0
            )
        }
        companion object: RootDataModel<MarykObject>(definitions = listOf(
                Def(Properties.string, MarykObject::string)
        ))
    }

    @Test
    fun testKey() {
        TestMarykObject.key.getKey(
                TestMarykObject(
                        string = "name",
                        int = 5123123,
                        uint = 555.toUInt32(),
                        double = 6.33,
                        bool = true,
                        enum = Option.V2,
                        dateTime = DateTime.nowUTC()
                )
        ) shouldBe Bytes(
                byteArrayOf(0, 0, 2, 43, 1, 1, 1, 0, 2)
        )
    }

    @Test
    fun testUUIDKey() {
        val key = MarykObject.key.getKey(
                MarykObject(
                        string = "name"
                )
        )

        key.size shouldBe 16
        key.toHex() shouldNotBe "00000000000000000000000000000000"

        val (first, second) = MarykObject.key.keyDefinitions.get(0).convertFromBytes(key.bytes, 0) as Pair<*, *>
        (first is Long) shouldBe true
        (second is Long) shouldBe true

    }
}