package maryk.core.properties.enum

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.properties.definitions.contextual.MultiTypeDefinitionContext
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.DefinitionsContext
import maryk.core.yaml.MarykYamlModelReader
import maryk.test.models.MarykTypeEnum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.expect

class MultiTypeEnumTest {
    val context = DefinitionsContext()

    @Test
    fun hasReservedIndex() {
        assertFailsWith<IllegalArgumentException> {
            object : MultiTypeEnumDefinition<MarykTypeEnum<*>>(optionalCases = MarykTypeEnum.cases,name = "MarykTypeEnum",  reservedIndices = listOf(1u), reservedNames = listOf("name")) {}.check()
        }
    }

    @Test
    fun hasReservedName() {
        assertFailsWith<IllegalArgumentException> {
            object : MultiTypeEnumDefinition<MarykTypeEnum<*>>(name = "MarykTypeEnum", optionalCases = MarykTypeEnum.cases, reservedNames = listOf("T2")) {}.check()
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        @Suppress("UNCHECKED_CAST")
        checkProtoBufConversion(
            MarykTypeEnum,
            MultiTypeEnumDefinition.Model as IsTypedObjectDataModel<MarykTypeEnum.Companion, IsObjectDataModel<MarykTypeEnum.Companion>, ContainsDefinitionsContext, MultiTypeDefinitionContext>,
            { context },
            ::compareEnumDefinitions
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        @Suppress("UNCHECKED_CAST")
        checkJsonConversion(
            MarykTypeEnum,
            MultiTypeEnumDefinition.Model as IsTypedObjectDataModel<MarykTypeEnum.Companion, IsObjectDataModel<MarykTypeEnum.Companion>, DefinitionsContext, DefinitionsContext>,
            { context },
            ::compareEnumDefinitions
        )
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        expect(
            """
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
        ) {
            @Suppress("UNCHECKED_CAST")
            checkYamlConversion(
                MarykTypeEnum,
                MultiTypeEnumDefinition.Model as IsTypedObjectDataModel<MarykTypeEnum.Companion, IsObjectDataModel<MarykTypeEnum.Companion>, DefinitionsContext, DefinitionsContext>,
                { context },
                ::compareEnumDefinitions
            )
        }
    }

    @Test
    fun readEnumFromYamlWithoutValues() {
        val reader = MarykYamlModelReader(
            """
            name: MarykTypeEnum
            """.trimIndent()
        )

        val enum = IndexedEnumDefinition.Model.Serializer.readJson(
            reader
        ).toDataObject()

        expect("MarykTypeEnum") { enum.name }
        assertNull(enum.optionalCases)
    }
}

internal fun compareEnumDefinitions(
    value: MultiTypeEnumDefinition<*>,
    against: MultiTypeEnumDefinition<*>
) {
    assertEquals(against.name, value.name)
    assertEquals(against.cases().size, value.cases().size)

    val valueMap = value.cases().map { Pair(it.index, it.name) }.toMap()

    for (enum in against.cases()) {
        assertEquals(enum.name, valueMap[enum.index])
    }
}
