package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Version
import maryk.core.properties.types.numeric.SInt32
import maryk.test.models.ModelV1.Properties

object ModelV1 : RootDataModel<ModelV1, Properties>(
    version = Version(1),
    properties = Properties
) {
    override val name = "Model"

    object Properties : PropertyDefinitions() {
        val value by string(index = 1u, default = "haha", regEx = "ha.*")
    }

    operator fun invoke(value: String = "haha") = values {
        mapNonNulls(Properties.value with value)
    }
}

object ModelV1_1 : RootDataModel<ModelV1_1, ModelV1_1.Properties>(
    version = Version(1, 1),
    properties = Properties
) {
    override val name = "Model"

    object Properties : PropertyDefinitions() {
        val value by string(index = 1u, default = "haha", regEx = "ha.*")
        val newNumber by number(index = 2u, type = SInt32, required = false)
    }

    operator fun invoke(
        value: String = "haha",
        newNumber: Int?
    ) = values {
        mapNonNulls(Properties.value with value, Properties.newNumber with newNumber)
    }
}

object ModelV2 : RootDataModel<ModelV2, ModelV2.Properties>(
    version = Version(2),
    properties = Properties
) {
    override val name = "Model"

    object Properties : PropertyDefinitions() {
        val value by string(index = 1u, default = "haha", regEx = "ha.*")
        val newNumber by number(index = 2u, type = SInt32, required = true)
    }

    operator fun invoke(
        value: String = "haha",
        newNumber: Int?
    ) = values {
        mapNonNulls(Properties.value with value, Properties.newNumber with newNumber)
    }
}

object ModelWrong : RootDataModel<ModelWrong, ModelWrong.Properties>(
    version = Version(2),
    properties = Properties
) {
    override val name = "Model"

    object Properties : PropertyDefinitions() {
        val value by number(index = 1u,  type = SInt32)
    }

    operator fun invoke(
        value: Int
    ) = values {
        mapNonNulls(Properties.value with value)
    }
}

object ModelMissing : RootDataModel<ModelMissing, ModelMissing.Properties>(
    version = Version(1, 2),
    properties = Properties
) {
    override val name = "Model"

    object Properties : PropertyDefinitions() {
        val newNumber by number(index = 2u, type = SInt32, required = true)
    }

    operator fun invoke(
        newNumber: Int?
    ) = values {
        mapNonNulls(Properties.newNumber with newNumber)
    }
}
