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
import maryk.test.assertType
import kotlin.test.Test
import kotlin.test.expect

class WriteValuesToStorageKtTest {
    @Test
    fun writeValuesToStorage() {
        var counter = 0
        testMaryk.writeToStorage { type: StorageTypeEnum<*>, bytes: ByteArray, definition: IsPropertyDefinition<*>, value ->
            valuesAsStorables[counter].let { (hex, compareValue) ->
                expect(hex) { bytes.toHex() }
                expect(compareValue) { value }
            }
            when (type) {
                Value -> {
                    assertType<IsSimpleValueDefinition<*, *>>(type.castDefinition(definition))
                }
                ListSize -> {
                    assertType<IsListDefinition<*, *>>(type.castDefinition(definition))
                }
                SetSize -> {
                    assertType<IsSetDefinition<*, *>>(type.castDefinition(definition))
                }
                MapSize -> {
                    assertType<IsMapDefinition<*, *, *>>(type.castDefinition(definition))
                }
                TypeValue -> {
                    assertType<IsMultiTypeDefinition<*, *, *>>(type.castDefinition(definition))
                }
                Embed -> {
                    assertType<EmbeddedValuesDefinition<*, *>>(type.castDefinition(definition))
                }
                ObjectDelete -> {
                    // Not in this write
                }
            }
            counter++
        }
        expect(26) { counter }
    }

    @Test
    fun writeComplexValuesToStorage() {
        var counter = 0
        complexValues.writeToStorage { _: StorageTypeEnum<*>, bytes: ByteArray, _: IsPropertyDefinition<*>, value ->
            complexValuesAsStorables[counter].let { (hex, compareValue) ->
                expect(hex) { bytes.toHex() }
                expect(compareValue) { value }
            }
            counter++
        }
        expect(49) { counter }
    }
}
