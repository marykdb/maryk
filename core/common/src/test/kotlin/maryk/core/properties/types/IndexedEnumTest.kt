package maryk.core.properties.types

import maryk.Option
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.QueryDataModel
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class IndexedEnumTest {
    private val context = DataModelPropertyContext(mapOf())

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        @Suppress("UNCHECKED_CAST")
        checkProtoBufConversion(
            Option,
            IndexedEnumDefinition.Model as QueryDataModel<Option.Companion>,
            context,
            ::compareEnumDefs
        )
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        @Suppress("UNCHECKED_CAST")
        checkJsonConversion(
            Option,
            IndexedEnumDefinition.Model as QueryDataModel<Option.Companion>,
            context,
            ::compareEnumDefs
        )
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        @Suppress("UNCHECKED_CAST")
        checkJsonConversion(
            Option,
            IndexedEnumDefinition.Model as QueryDataModel<Option.Companion>,
            context,
            ::compareEnumDefs
        )
    }

    private fun compareEnumDefs(
        value: IndexedEnumDefinition<*>,
        against: IndexedEnumDefinition<*>
    ) {
        value.name shouldBe against.name
        value.values().size shouldBe against.values().size

        val valueMap = value.values().map { Pair(it.index, it.name) }.toMap()

        for (enum in against.values()) {
            valueMap.get(enum.index) shouldBe enum.name
        }
    }
}
