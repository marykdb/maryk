package maryk.core.properties.definitions.index

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.query.DefinitionsConversionContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.expect

class MultipleTest {
    object FanoutModel : RootDataModel<FanoutModel>() {
        val family by string(index = 1u, final = true)
        val given by set(
            index = 2u,
            required = false,
            final = true,
            valueDefinition = StringDefinition()
        )
    }

    private val multiple = TestMarykModel.run {
        Multiple(
            UUIDv4Key,
            Reversed(bool.ref()),
            multi.typeRef(),
            string.ref(),
            Reversed(string.ref()),
            int.ref()
        )
    }

    private val context = DefinitionsConversionContext(
        propertyDefinitions = TestMarykModel
    )

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(
            value = multiple,
            dataModel = Multiple.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(
            value = multiple,
            dataModel = Multiple.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        expect(
            """
            - !UUIDv4
            - !Reversed bool
            - !Ref multi.*
            - !Ref string
            - !Reversed string
            - !Ref int

            """.trimIndent()
        ) {
            checkYamlConversion(
                value = multiple,
                dataModel = Multiple.Model,
                context = { context }
            )
        }
    }

    @Test
    fun toReferenceStorageBytes() {
        expect("040101020b31020a69020a09020b09020a11") { multiple.toReferenceStorageByteArray().toHexString() }
    }

    @Test
    fun checkedIndexByteLengthRejectsOverflow() {
        assertFailsWith<IllegalArgumentException> {
            Int.MAX_VALUE.checkedIndexByteLengthPlus(1)
        }
    }

    @Test
    fun checkedIndexByteLengthRejectsNegativeAddend() {
        assertFailsWith<IllegalArgumentException> {
            0.checkedIndexByteLengthPlus(-1)
        }
    }

    @Test
    fun calculateStorageByteLengthForIndexUsesLongestFanoutCombination() {
        val index = Multiple(
            FanoutModel.family.ref(),
            AnyOf(
                FanoutModel.family.ref(),
                FanoutModel { given.refToAny() }
            )
        )
        val values = FanoutModel.create {
            family with "abc"
            given with setOf("z", "longer")
        }

        val lengths = index.toStorageByteArraysForIndex(values).map { it.size }
        assertEquals(lengths.max(), index.calculateStorageByteLengthForIndex(values))
    }
}
