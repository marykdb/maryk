package maryk.core.models

import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.test.models.TestMarykModel
import maryk.test.shouldThrow
import kotlin.test.Test

object WrongModelIndex : RootDataModel<WrongModelIndex, WrongModelIndex.Properties>(
    properties = Properties,
    reservedIndices = listOf(1)
) {
    object Properties : PropertyDefinitions() {
        val value =
            add(1, "value", StringDefinition())
    }

    operator fun invoke(value: String) = this.values {
        mapNonNulls(this.value with value)
    }
}

object WrongModelName : RootDataModel<WrongModelName, WrongModelName.Properties>(
    properties = Properties,
    reservedNames = listOf("value")
) {
    object Properties : PropertyDefinitions() {
        val value =
            add(1, "value", StringDefinition())
    }

    operator fun invoke(value: String) = this.values {
        mapNonNulls(this.value with value)
    }
}

internal class WrongRootDataModelTest {
    @Test
    fun checkDataModel() {
        TestMarykModel.check()

        shouldThrow<IllegalArgumentException> {
            WrongModelIndex.check()
        }

        shouldThrow<IllegalArgumentException> {
            WrongModelName.check()
        }
    }
}
