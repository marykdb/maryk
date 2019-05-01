package maryk.core.processors.datastore

import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.properties.definitions.EmbeddedValuesDefinition
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

class WriteValuesToStorageKtTest {
    @Test
    fun writeValuesToStorage() {
        var counter = 0
        testMaryk.writeToStorage { type: StorageTypeEnum<*>, bytes: ByteArray, definition: IsPropertyDefinition<*>, value ->
            valuesAsStorables[counter].let { (hex, compareValue) ->
                bytes.toHex() shouldBe hex
                value shouldBe compareValue
            }
            when (type) {
                Value -> {
                    shouldBeOfType<IsSimpleValueDefinition<*, *>>(type.castDefinition(definition))
                }
                ListSize -> {
                    shouldBeOfType<IsListDefinition<*, *>>(type.castDefinition(definition))
                }
                SetSize -> {
                    shouldBeOfType<IsSetDefinition<*, *>>(type.castDefinition(definition))
                }
                MapSize -> {
                    shouldBeOfType<IsMapDefinition<*, *, *>>(type.castDefinition(definition))
                }
                TypeValue -> {
                    shouldBeOfType<IsMultiTypeDefinition<*, *>>(type.castDefinition(definition))
                }
                Embed -> {
                    shouldBeOfType<EmbeddedValuesDefinition<*, *>>(type.castDefinition(definition))
                }
                ObjectDelete -> {
                    // Not in this write
                }
            }
            counter++
        }
        counter shouldBe 26
    }

    @Test
    fun writeComplexValuesToStorage() {
        var counter = 0
        complexValues.writeToStorage { _: StorageTypeEnum<*>, bytes: ByteArray, _: IsPropertyDefinition<*>, value ->
            complexValuesAsStorables[counter].let { (hex, compareValue) ->
                bytes.toHex() shouldBe hex
                value shouldBe compareValue
            }
            counter++
        }
        counter shouldBe 49
    }
}
