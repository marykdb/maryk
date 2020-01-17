package maryk.core.models

import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith

object WrongModelIndex : RootDataModel<WrongModelIndex, WrongModelIndex.Properties>(
    properties = Properties,
    reservedIndices = listOf(1u)
) {
    object Properties : PropertyDefinitions() {
        val value by
            wrap(1u) { StringDefinition() }
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
        val value by wrap (1u) { StringDefinition() }
    }

    operator fun invoke(value: String) = this.values {
        mapNonNulls(this.value with value)
    }
}

internal class WrongRootDataModelTest {
    @Test
    fun checkDataModel() {
        TestMarykModel.check()

        assertFailsWith<IllegalArgumentException> {
            WrongModelIndex.check()
        }

        assertFailsWith<IllegalArgumentException> {
            WrongModelName.check()
        }
    }
}
