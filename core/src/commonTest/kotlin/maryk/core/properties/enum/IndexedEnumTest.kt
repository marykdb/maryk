package maryk.core.properties.enum

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.AbstractObjectDataModel
import maryk.core.properties.IsBaseModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.DefinitionsContext
import maryk.core.yaml.MarykYamlModelReader
import maryk.test.models.Option
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.expect

class IndexedEnumTest {
    @Test
    fun hasReservedIndex() {
        assertFailsWith<IllegalArgumentException> {
            object : IndexedEnumDefinition<Option>(optionalCases = Option.cases,name = "Option",  reservedIndices = listOf(1u), reservedNames = listOf("name")) {}.check()
        }
    }

    @Test
    fun hasReservedName() {
        assertFailsWith<IllegalArgumentException> {
            object : IndexedEnumDefinition<Option>(name = "Option", optionalCases = Option.cases, reservedNames = listOf("V2")) {}.check()
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        @Suppress("UNCHECKED_CAST")
        checkProtoBufConversion(
            Option,
            IndexedEnumDefinition.Model as IsBaseModel<Option.Companion, ObjectPropertyDefinitions<Option.Companion>, ContainsDefinitionsContext, EnumNameContext>,
            null,
            ::compareEnumDefinitions
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        @Suppress("UNCHECKED_CAST")
        checkJsonConversion(
            Option,
            IndexedEnumDefinition.Model.Model as AbstractObjectDataModel<Option.Companion, ObjectPropertyDefinitions<Option.Companion>, DefinitionsContext, DefinitionsContext>,
            null,
            ::compareEnumDefinitions
        )
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        expect(
            """
            name: Option
            cases:
              1: V1
              2: [V2, VERSION2]
              3: [V3, VERSION3]
            reservedIndices: [4]
            reservedNames: [V4]

            """.trimIndent()
        ) {
            @Suppress("UNCHECKED_CAST")
            checkYamlConversion(
                Option,
                IndexedEnumDefinition.Model.Model as AbstractObjectDataModel<Option.Companion, ObjectPropertyDefinitions<Option.Companion>, DefinitionsContext, DefinitionsContext>,
                null,
                ::compareEnumDefinitions
            )
        }
    }

    @Test
    fun readEnumFromYamlWithoutValues() {
        val reader = MarykYamlModelReader(
            """
            name: Option
            """.trimIndent()
        )

        val enum = IndexedEnumDefinition.Model.Model.readJson(
            reader
        ).toDataObject()

        expect("Option") { enum.name }
        expect(null) { enum.optionalCases }
    }
}

internal fun compareEnumDefinitions(
    value: IndexedEnumDefinition<*>,
    against: IndexedEnumDefinition<*>
) {
    assertEquals(against.name, value.name)
    assertEquals(against.cases().size, value.cases().size)

    val valueMap = value.cases().associate { it.index to it.name }

    for (enum in against.cases()) {
        expect(enum.name) { valueMap[enum.index] }
    }
}
