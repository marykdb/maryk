@file:Suppress("unused")

package maryk.test.models

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.reference
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Version
import maryk.core.properties.types.numeric.SInt32

object ModelV1 : RootDataModel<ModelV1>(
    name = "Model",
    version = Version(1),
    indices = {
        listOf(
            ModelV1.value.ref()
        )
    },
) {
    val value by string(index = 1u, default = "haha", regEx = "ha.*")
}

object ModelV1_1WrongKey : RootDataModel<ModelV1_1WrongKey>(
    name = "Model",
    version = Version(1),
    keyDefinition = {
        ModelV1_1WrongKey.newNumber.ref()
    },
) {
    val value by string(index = 1u, default = "haha", regEx = "ha.*")
    val newNumber by number(index = 2u, type = SInt32, required = true, final = true)
}

object ModelV1_1 : RootDataModel<ModelV1_1>(
    name = "Model",
    version = Version(1, 1),
) {
    val value by string(index = 1u, default = "haha", regEx = "ha.*")
    val newNumber by number(index = 2u, type = SInt32, required = false)
}

object ModelV2 : RootDataModel<ModelV2>(
    name = "Model",
    version = Version(2),
    indices = { listOf(
        ModelV2.value.ref()
    ) },
) {
    val value by string(index = 1u, default = "haha", regEx = "ha.*")
    val newNumber by number(index = 2u, type = SInt32, required = true)
}

object ModelV2ExtraIndex : RootDataModel<ModelV2ExtraIndex>(
    name = "Model",
    version = Version(2),
    indices = {
        listOf(
            ModelV2ExtraIndex.value.ref(),
            ModelV2ExtraIndex.newNumber.ref()
        )
    },
) {
    val value by string(index = 1u, default = "haha", regEx = "ha.*")
    val newNumber by number(index = 2u, type = SInt32, required = true)
}

object ModelWrongValueType : RootDataModel<ModelWrongValueType>(
    name = "Model",
    version = Version(2),
) {
    val value by number(index = 1u,  type = SInt32)
}

object ModelMissingProperty : RootDataModel<ModelMissingProperty>(
    name = "Model",
    version = Version(1, 2),
) {
    val newNumber by number(index = 2u, type = SInt32, required = true)
}

object ModelV2ReservedNamesAndIndices : RootDataModel<ModelV2ReservedNamesAndIndices>(
    name = "Model",
    version = Version(1, 2),
    reservedNames = listOf("value"),
    reservedIndices = listOf(1u),
) {
    val newNumber by number(index = 2u, type = SInt32, required = false)
}

object DependentModel : DataModel<DependentModel>() {
    val value by string(index = 1u, default = "haha", regEx = "ha.*")
    val deeperEnum by enum(index = 2u, enum = Severity)
    val embed by embed(index = 3u, dataModel = { SimpleMarykModel })
    val selfEmbed by embed(index = 4u, dataModel = { DependentModel })
}

object ModelWithDependents : RootDataModel<ModelWithDependents>(
    version = Version(1, 0),
    indices = { listOf(
        ModelWithDependents { dep { value::ref } }
    ) }
) {
    val dep by embed(index = 1u, dataModel = { DependentModel })
    val enum by enum(index = 2u, enum = Option)
    val selfRef by reference(index = 3u, dataModel = { ModelWithDependents })
}
