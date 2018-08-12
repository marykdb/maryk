package maryk.core.properties.enum

import maryk.Option
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.AbstractObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.DefinitionsContext
import maryk.core.yaml.createMarykYamlModelReader
import maryk.test.shouldBe
import kotlin.test.Test

class IndexedEnumTest {
    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        @Suppress("UNCHECKED_CAST")
        checkProtoBufConversion(
            Option,
            IndexedEnumDefinition.Model as AbstractObjectDataModel<Option.Companion, ObjectPropertyDefinitions<Option.Companion>, DefinitionsContext, DefinitionsContext>,
            null,
            ::compareEnumDefinitions
        )
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        @Suppress("UNCHECKED_CAST")
        checkJsonConversion(
            Option,
            IndexedEnumDefinition.Model as AbstractObjectDataModel<Option.Companion, ObjectPropertyDefinitions<Option.Companion>, DefinitionsContext, DefinitionsContext>,
            null,
            ::compareEnumDefinitions
        )
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        @Suppress("UNCHECKED_CAST")
        checkYamlConversion(
            Option,
            IndexedEnumDefinition.Model as AbstractObjectDataModel<Option.Companion, ObjectPropertyDefinitions<Option.Companion>, DefinitionsContext, DefinitionsContext>,
            null,
            ::compareEnumDefinitions
        ) shouldBe """
        name: Option
        values:
          1: V1
          2: V2
          3: V3

        """.trimIndent()
    }

    @Test
    fun read_enum_from_yaml_without_values() {
        val reader = createMarykYamlModelReader(
            """
            name: Option
            """.trimIndent()
        )

        val enum = IndexedEnumDefinition.Model.readJson(
            reader
        ).toDataObject()

        enum.name shouldBe "Option"
        enum.optionalValues shouldBe null
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
