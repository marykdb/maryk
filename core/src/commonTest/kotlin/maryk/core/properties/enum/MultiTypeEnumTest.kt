package maryk.core.properties.enum

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.AbstractObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.DefinitionsContext
import maryk.core.yaml.createMarykYamlModelReader
import maryk.test.models.MarykTypeEnum
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

class MultiTypeEnumTest {
    val context = DefinitionsContext()

    @Test
    fun hasReservedIndex() {
        shouldThrow<IllegalArgumentException> {
            object : MultiTypeEnumDefinition<MarykTypeEnum<*>>(optionalCases = MarykTypeEnum.cases,name = "MarykTypeEnum",  reservedIndices = listOf(1u), reservedNames = listOf("name")) {}.check()
        }
    }

    @Test
    fun hasReservedName() {
        shouldThrow<IllegalArgumentException> {
            object : MultiTypeEnumDefinition<MarykTypeEnum<*>>(name = "MarykTypeEnum", optionalCases = MarykTypeEnum.cases, reservedNames = listOf("T2")) {}.check()
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        @Suppress("UNCHECKED_CAST")
        checkProtoBufConversion(
            MarykTypeEnum,
            MultiTypeEnumDefinition.Model as AbstractObjectDataModel<MarykTypeEnum.Companion, ObjectPropertyDefinitions<MarykTypeEnum.Companion>, DefinitionsContext, DefinitionsContext>,
            { context },
            ::compareEnumDefinitions
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        @Suppress("UNCHECKED_CAST")
        checkJsonConversion(
            MarykTypeEnum,
            MultiTypeEnumDefinition.Model as AbstractObjectDataModel<MarykTypeEnum.Companion, ObjectPropertyDefinitions<MarykTypeEnum.Companion>, DefinitionsContext, DefinitionsContext>,
            { context },
            ::compareEnumDefinitions
        )
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        @Suppress("UNCHECKED_CAST")
        checkYamlConversion(
            MarykTypeEnum,
            MultiTypeEnumDefinition.Model as AbstractObjectDataModel<MarykTypeEnum.Companion, ObjectPropertyDefinitions<MarykTypeEnum.Companion>, DefinitionsContext, DefinitionsContext>,
            { context },
            ::compareEnumDefinitions
        ) shouldBe """
        name: MarykTypeEnum
        cases:
          ? 1: [T1, Type1]
          : !String
            required: true
            final: false
            unique: false
            regEx: '[^&]+'
          ? 2: T2
          : !Number
            required: true
            final: false
            unique: false
            type: SInt32
            maxValue: 2000
            random: false
          ? 3: T3
          : !Embed
            required: true
            final: false
            dataModel: EmbeddedMarykModel
          ? 4: T4
          : !List
            required: true
            final: false
            valueDefinition: !String
              required: true
              final: false
              unique: false
              regEx: '[^&]+'
          ? 5: T5
          : !Set
            required: true
            final: false
            valueDefinition: !String
              required: true
              final: false
              unique: false
              regEx: '[^&]+'
          ? 6: T6
          : !Map
            required: true
            final: false
            keyDefinition: !Number
              required: true
              final: false
              unique: false
              type: UInt32
              random: false
            valueDefinition: !String
              required: true
              final: false
              unique: false
              regEx: '[^&]+'
          ? 7: T7
          : !MultiType
            required: true
            final: false
            typeEnum:
              name: SimpleMarykTypeEnum
              cases:
                ? 1: [S1, Type1]
                : !String
                  required: true
                  final: false
                  unique: false
                  regEx: '[^&]+'
                ? 2: S2
                : !Number
                  required: true
                  final: false
                  unique: false
                  type: SInt16
                  random: false
                ? 3: S3
                : !Embed
                  required: true
                  final: false
                  dataModel: EmbeddedMarykModel
              reservedIndices: [99]
              reservedNames: [O99]
            typeIsFinal: true
        reservedIndices: [99]
        reservedNames: [O99]

        """.trimIndent()
    }

    @Test
    fun readEnumFromYamlWithoutValues() {
        val reader = createMarykYamlModelReader(
            """
            name: MarykTypeEnum
            """.trimIndent()
        )

        val enum = IndexedEnumDefinition.Model.readJson(
            reader
        ).toDataObject()

        enum.name shouldBe "MarykTypeEnum"
        enum.optionalCases shouldBe null
    }
}

internal fun compareEnumDefinitions(
    value: MultiTypeEnumDefinition<*>,
    against: MultiTypeEnumDefinition<*>
) {
    value.name shouldBe against.name
    value.cases().size shouldBe against.cases().size

    val valueMap = value.cases().map { Pair(it.index, it.name) }.toMap()

    for (enum in against.cases()) {
        valueMap[enum.index] shouldBe enum.name
    }
}
