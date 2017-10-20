package maryk.core.properties.definitions.key

import io.kotlintest.matchers.shouldBe
import maryk.core.extensions.toHex
import maryk.core.objects.Def
import maryk.core.objects.RootDataModel
import maryk.core.objects.definitions
import maryk.core.properties.ByteCollector
import maryk.core.properties.definitions.AbstractSubDefinition
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.TypedValue
import org.junit.Test

internal class TypeIdTest {
    private data class MarykObject(
            val multi: TypedValue<*>
    ){
        object Properties {
            val multi = MultiTypeDefinition(
                    name = "multi",
                    index = 0,
                    typeMap = mapOf<Int, AbstractSubDefinition<*>>(
                            0 to StringDefinition(),
                            1 to BooleanDefinition()
                    )
            )
        }
        companion object: RootDataModel<MarykObject>(
            constructor = { MarykObject(it[0] as TypedValue<*>) },
            keyDefinitions = definitions(
                    TypeId(Properties.multi)
            ),
            definitions = listOf(
                Def(Properties.multi, MarykObject::multi)
            )
        )
    }

    @Test
    fun testKey(){
        val obj = MarykObject(
                multi = TypedValue(1, true)
        )

        val key = MarykObject.key.getKey(obj)
        key.bytes.toHex() shouldBe "0001"

        val keyDef = MarykObject.key.keyDefinitions[0]

        (keyDef is TypeId) shouldBe true
        val specificDef = keyDef as TypeId
        specificDef.multiTypeDefinition shouldBe MarykObject.Properties.multi

        specificDef.getValue(MarykObject, obj) shouldBe 1

        val bc = ByteCollector()
        specificDef.writeStorageBytes(1, bc::reserve, bc::write)
        specificDef.readStorageBytes(bc.size, bc::read) shouldBe 1
    }
}