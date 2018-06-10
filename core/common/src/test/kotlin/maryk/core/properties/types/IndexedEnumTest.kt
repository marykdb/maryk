package maryk.core.properties.types

import maryk.Option
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.objects.AbstractDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelContext
import maryk.core.yaml.createMarykYamlModelReader
import maryk.test.shouldBe
import kotlin.test.Test

class IndexedEnumTest {
    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        @Suppress("UNCHECKED_CAST")
        checkProtoBufConversion(
            Option,
            IndexedEnumDefinition.Model as AbstractDataModel<Option.Companion, PropertyDefinitions<Option.Companion>, DataModelContext, DataModelContext>,
            null,
            ::compareEnumDefinitions
        )
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        @Suppress("UNCHECKED_CAST")
        checkJsonConversion(
            Option,
            IndexedEnumDefinition.Model as AbstractDataModel<Option.Companion, PropertyDefinitions<Option.Companion>, DataModelContext, DataModelContext>,
            null,
            ::compareEnumDefinitions
        )
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        @Suppress("UNCHECKED_CAST")
        checkYamlConversion(
            Option,
            IndexedEnumDefinition.Model as AbstractDataModel<Option.Companion, PropertyDefinitions<Option.Companion>, DataModelContext, DataModelContext>,
            null,
            ::compareEnumDefinitions
        ) shouldBe """
        name: Option
        values:
          0: V0
          1: V1
          2: V2

        """.trimIndent()
    }
}

internal fun compareEnumDefinitions(
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
