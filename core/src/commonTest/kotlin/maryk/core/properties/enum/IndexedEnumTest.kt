package maryk.core.properties.enum

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.AbstractObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.DefinitionsContext
import maryk.core.yaml.createMarykYamlModelReader
import maryk.test.models.Option
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

class IndexedEnumTest {
    @Test
    fun hasReservedIndex() {
        shouldThrow<IllegalArgumentException> {
            object : IndexedEnumDefinition<Option>(optionalCases = Option.cases,name = "Option",  reservedIndices = listOf(1u), reservedNames = listOf("name")) {}.check()
        }
    }

    @Test
    fun hasReservedName() {
        shouldThrow<IllegalArgumentException> {
            object : IndexedEnumDefinition<Option>(name = "Option", optionalCases = Option.cases, reservedNames = listOf("V2")) {}.check()
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        @Suppress("UNCHECKED_CAST")
        checkProtoBufConversion(
            Option,
            IndexedEnumDefinition.Model as AbstractObjectDataModel<Option.Companion, ObjectPropertyDefinitions<Option.Companion>, DefinitionsContext, DefinitionsContext>,
            null,
            ::compareEnumDefinitions
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        @Suppress("UNCHECKED_CAST")
        checkJsonConversion(
            Option,
            IndexedEnumDefinition.Model as AbstractObjectDataModel<Option.Companion, ObjectPropertyDefinitions<Option.Companion>, DefinitionsContext, DefinitionsContext>,
            null,
            ::compareEnumDefinitions
        )
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        @Suppress("UNCHECKED_CAST")
        checkYamlConversion(
            Option,
            IndexedEnumDefinition.Model as AbstractObjectDataModel<Option.Companion, ObjectPropertyDefinitions<Option.Companion>, DefinitionsContext, DefinitionsContext>,
            null,
            ::compareEnumDefinitions
        ) shouldBe """
        name: Option
        cases:
          1: V1
          2: [V2, VERSION2]
          3: [V3, VERSION3]
        reservedIndices: [4]
        reservedNames: [V4]

        """.trimIndent()
    }

    @Test
    fun readEnumFromYamlWithoutValues() {
        val reader = createMarykYamlModelReader(
            """
            name: Option
            """.trimIndent()
        )

        val enum = IndexedEnumDefinition.Model.readJson(
            reader
        ).toDataObject()

        enum.name shouldBe "Option"
        enum.optionalCases shouldBe null
    }
}

internal fun compareEnumDefinitions(
    value: IndexedEnumDefinition<*>,
    against: IndexedEnumDefinition<*>
) {
    value.name shouldBe against.name
    value.cases().size shouldBe against.cases().size

    val valueMap = value.cases().map { Pair(it.index, it.name) }.toMap()

    for (enum in against.cases()) {
        valueMap[enum.index] shouldBe enum.name
    }
}
