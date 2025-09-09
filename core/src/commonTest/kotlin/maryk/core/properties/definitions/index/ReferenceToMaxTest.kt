package maryk.core.properties.definitions.index

import kotlinx.datetime.LocalDate
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.date
import maryk.core.query.DefinitionsConversionContext
import maryk.lib.extensions.toHex
import kotlin.test.Test
import kotlin.test.expect

internal class ReferenceToMaxTest {
    object PeriodModel : RootDataModel<PeriodModel>(
        indexes = { listOf(
            Multiple(PeriodModel.startDate.ref(), ReferenceToMax(PeriodModel.endDate.ref()))
        )}
    ) {
        val startDate by date(1u)
        val endDate by date(2u, required = false)
    }

    private val context = DefinitionsConversionContext(
        propertyDefinitions = PeriodModel
    )

    @Test
    fun writesMaxWhenEndDateMissing() {
        val start = LocalDate(2020, 5, 1)
        val valuesWithoutEnd = PeriodModel.create {
            startDate += start
        }
        val valuesWithEnd = PeriodModel.create {
            startDate += start
            endDate += DateDefinition.MAX
        }
        val indexable = Multiple(
            PeriodModel.startDate.ref(),
            ReferenceToMax(PeriodModel.endDate.ref())
        )
        val without = indexable.toStorageByteArrayForIndex(valuesWithoutEnd)
        val withEnd = indexable.toStorageByteArrayForIndex(valuesWithEnd)
        expect(withEnd!!.toHex()) { without!!.toHex() }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(
            value = ReferenceToMax(PeriodModel.endDate.ref()),
            dataModel = ReferenceToMax.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(
            value = ReferenceToMax(PeriodModel.endDate.ref()),
            dataModel = ReferenceToMax.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        expect("endDate") {
            checkYamlConversion(
                value = ReferenceToMax(PeriodModel.endDate.ref()),
                dataModel = ReferenceToMax.Model,
                context = { context }
            )
        }
    }

    @Test
    fun toReferenceStorageBytes() {
        expect("0d11") {
            ReferenceToMax(PeriodModel.endDate.ref()).toReferenceStorageByteArray().toHex()
        }
    }
}
