package maryk.core.properties.definitions.contextual

import maryk.checkProtoBufConversion
import maryk.core.models.DataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.TypedValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.ContextualDefinitionWrapper
import maryk.core.protobuf.WriteCache
import maryk.core.values.ValuesImpl
import maryk.test.ByteCollector
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.expect

private class EmbeddedValueContext : IsPropertyContext {
    val valueDefinition = StringDefinition()
}

private object ContextualValueModel : DataModel<ContextualValueModel>() {
    private val contextualStringDefinition =
        ContextualValueDefinition<EmbeddedValueContext, IsPropertyContext, String, IsValueDefinition<String, IsPropertyContext>>(
            contextualResolver = { context: EmbeddedValueContext? ->
                context!!.valueDefinition
            }
        )

    val value = ContextualDefinitionWrapper<
        String,
        String,
        EmbeddedValueContext,
        ContextualValueDefinition<EmbeddedValueContext, IsPropertyContext, String, IsValueDefinition<String, IsPropertyContext>>,
        Any
    >(
        index = 1u,
        name = "value",
        definition = contextualStringDefinition
    ).also(this::addSingle)
}

class ContextualEmbeddedValuesDefinitionTest {
    private val subModelsToTest = listOf(
        SimpleMarykModel.create { value with "test1" },
        SimpleMarykModel.create { value with "test2" }
    )

    @Suppress("UNCHECKED_CAST")
    private val def = ContextualEmbeddedValuesDefinition<ModelContext>(
        contextualResolver = { it!!.model!!.invoke() as TypedValuesDataModel<IsValuesDataModel> }
    )

    private val context = ModelContext(
        definitionsContext = null,
        model = { SimpleMarykModel }
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        for (value in subModelsToTest) {
            @Suppress("UNCHECKED_CAST")
            checkProtoBufConversion(
                bc,
                value as ValuesImpl,
                this.def,
                this.context
            )
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun calculatesLengthWithContext() {
        val contextualDef = ContextualEmbeddedValuesDefinition<EmbeddedValueContext>(
            contextualResolver = { ContextualValueModel as TypedValuesDataModel<IsValuesDataModel> }
        )
        val value = ContextualValueModel.create {
            value with "test"
        } as ValuesImpl
        val cache = WriteCache()
        val length = contextualDef.calculateTransportByteLength(value, cache, EmbeddedValueContext())
        val bc = ByteCollector()

        bc.reserve(length)
        contextualDef.writeTransportBytes(value, cache, bc::write, EmbeddedValueContext())

        assertEquals(length, bc.size)
    }

    @Test
    fun convertString() {
        for (subModel in subModelsToTest) {
            @Suppress("UNCHECKED_CAST")
            val b = def.asString(
                subModel as ValuesImpl,
                this.context
            )
            expect(subModel) { def.fromString(b, this.context) }
        }
    }

    @Test
    fun fromStringRejectsTrailingContent() {
        assertFailsWith<Throwable> {
            def.fromString("""{"value":"test1"}{}""", this.context)
        }
        assertFailsWith<Throwable> {
            def.fromString("""{"value":"test1"} trailing""", this.context)
        }
    }
}
