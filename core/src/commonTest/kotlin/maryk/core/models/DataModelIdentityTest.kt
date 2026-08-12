package maryk.core.models

import maryk.core.models.definitions.DataModelDefinition
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.string
import maryk.core.properties.types.ValueDataObject
import maryk.core.values.ObjectValues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

private const val SAME_NAME = "SameName"

private object SameNameStringDataModel : DataModel<SameNameStringDataModel>({
    DataModelDefinition(SAME_NAME)
}) {
    val value by string(1u)
}

private object SameNameBooleanDataModel : DataModel<SameNameBooleanDataModel>({
    DataModelDefinition(SAME_NAME)
}) {
    val value by boolean(1u)
}

private object SameNameStringRootModel : RootDataModel<SameNameStringRootModel>(name = SAME_NAME) {
    val value by string(1u)
}

private object SameNameBooleanRootModel : RootDataModel<SameNameBooleanRootModel>(name = SAME_NAME) {
    val value by boolean(1u)
}

private class IdentityValueObject : ValueDataObject(byteArrayOf())

private object SameNameStringValueModel :
    ValueDataModel<IdentityValueObject, SameNameStringValueModel>(SAME_NAME) {
    val value by string(1u, getter = { _: IdentityValueObject -> null })

    override fun invoke(values: ObjectValues<IdentityValueObject, SameNameStringValueModel>) =
        IdentityValueObject()
}

private object SameNameBooleanValueModel :
    ValueDataModel<IdentityValueObject, SameNameBooleanValueModel>(SAME_NAME) {
    val value by boolean(1u, getter = { _: IdentityValueObject -> null })

    override fun invoke(values: ObjectValues<IdentityValueObject, SameNameBooleanValueModel>) =
        IdentityValueObject()
}

internal class DataModelIdentityTest {
    @Test
    fun modelsWithSameNameAndPropertyNamesKeepReferentialIdentity() {
        assertNotEquals(SameNameStringDataModel as Any, SameNameBooleanDataModel as Any)
        assertNotEquals(SameNameStringRootModel as Any, SameNameBooleanRootModel as Any)
        assertNotEquals(SameNameStringValueModel as Any, SameNameBooleanValueModel as Any)

        assertEquals(
            6,
            hashSetOf<Any>(
                SameNameStringDataModel,
                SameNameBooleanDataModel,
                SameNameStringRootModel,
                SameNameBooleanRootModel,
                SameNameStringValueModel,
                SameNameBooleanValueModel,
            ).size
        )
    }

    @Test
    fun valuesFromDistinctModelsDoNotCollapseInHashSets() {
        val stringValues = SameNameStringDataModel.create { value with "value" }
        val booleanValues = SameNameBooleanDataModel.create { value with true }

        assertNotEquals(stringValues as Any, booleanValues as Any)
        assertEquals(2, hashSetOf<Any>(stringValues, booleanValues).size)
    }
}
