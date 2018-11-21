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
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

class WalkValuesForStorageKtTest {
    @Test
    fun testWalkValues() {
        var counter = 0
        testMaryk.walkForStorage { type: StorageTypeEnum<*>, bytes: ByteArray, definition: IsPropertyDefinition<*>, value ->
            valuesAsStorables[counter].let { (hex, compareValue) ->
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
