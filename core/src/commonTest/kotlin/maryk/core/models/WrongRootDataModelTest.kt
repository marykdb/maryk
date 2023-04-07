package maryk.core.models

import maryk.core.properties.definitions.string
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith

object WrongModelIndex : RootDataModel<WrongModelIndex>(
    reservedIndices = listOf(1u),
) {
    val value by string(1u)
}

object WrongModelName : RootDataModel<WrongModelName>(
    reservedNames = listOf("value"),
) {
    val value by string (1u)
}

internal class WrongRootDataModelTest {
    @Test
    fun checkDataModel() {
        TestMarykModel.checkModel()

        assertFailsWith<IllegalArgumentException> {
            WrongModelIndex.checkModel()
        }

        assertFailsWith<IllegalArgumentException> {
            WrongModelName.checkModel()
        }
    }
}
