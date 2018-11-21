@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.processors.datastore

import maryk.core.processors.datastore.StorageTypeEnum.ListCount
import maryk.core.processors.datastore.StorageTypeEnum.MapCount
import maryk.core.processors.datastore.StorageTypeEnum.SetCount
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.lib.extensions.toHex
import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

class WalkValuesForStorageKtTest {
    @Test
    fun testWalkValues() {
        val testMaryk = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = DateTime(2018, 7, 18),
            listOfString = listOf(
                "v1", "v2", "v3"
            ),
            set = setOf(
                Date(2018, 9, 9),
                Date(1981, 12, 5),
                Date(1989, 5, 15)
            ),
            map = mapOf(
                Time(11, 22, 33) to "eleven",
                Time(12, 23, 34) to "twelve"
            ),
            embeddedValues = EmbeddedMarykModel(
                value = "test",
                model = EmbeddedMarykModel(
                    value = "another test"
                )
            )
        )

        val checkAgainst = arrayOf<Pair<String, Any>>(
            "09" to testMaryk { string }!!,
            "11" to testMaryk { int }!!,
            "19" to testMaryk { uint }!!,
            "21" to testMaryk { double }!!,
            "29" to testMaryk { dateTime }!!,
            "39" to testMaryk { enum }!!,
            "4b" to 3,
            "4b80004577" to Date(2018, 9, 9),
            "4b80001104" to Date(1981, 12, 5),
            "4b80001ba2" to Date(1989, 5, 15),
            "54" to 2,
            "54009ff9" to "eleven",
            "5400ae46" to "twelve",
            "6109" to testMaryk { embeddedValues }!!{ value }!!,
            "611109" to testMaryk { embeddedValues }!!{ model }!!{ value }!!,
            "7a" to 3,
            "7a00000000" to "v1",
            "7a00000001" to "v2",
            "7a00000002" to "v3"
        )

        var counter = 0
        testMaryk.walkForStorage { type: StorageTypeEnum<*>, bytes: ByteArray, definition: IsPropertyDefinition<*>, value ->
            checkAgainst[counter].let { (hex, compareValue) ->
                bytes.toHex() shouldBe hex
                value shouldBe compareValue
            }
            when (type) {
                Value -> {
                    shouldBeOfType<IsSimpleValueDefinition<*, *>>(type.castDefinition(definition))
                }
                ListCount -> {
                    shouldBeOfType<IsListDefinition<*, *>>(type.castDefinition(definition))
                }
                SetCount -> {
                    shouldBeOfType<IsSetDefinition<*, *>>(type.castDefinition(definition))
                }
                MapCount -> {
                    shouldBeOfType<IsMapDefinition<*, *, *>>(type.castDefinition(definition))
                }
                TypeValue -> {
                    shouldBeOfType<IsMultiTypeDefinition<*, *>>(type.castDefinition(definition))
                }
            }
            counter++
        }
        counter shouldBe 19
    }
}
