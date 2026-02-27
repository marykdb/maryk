@file:Suppress("unused")

package maryk.core.models.migration

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.reference
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

object MigrationBaseModel : RootDataModel<MigrationBaseModel>(
    name = "MigrationBaseModel",
    version = Version(1),
) {
    val value by string(index = 1u)
}

object MigrationDependentModel : RootDataModel<MigrationDependentModel>(
    name = "MigrationDependentModel",
    version = Version(1),
) {
    val baseRef by reference(index = 1u, required = false, dataModel = { MigrationBaseModel })
}

object MigrationCycleLeftModel : RootDataModel<MigrationCycleLeftModel>(
    name = "MigrationCycleLeftModel",
    version = Version(1),
) {
    val rightRef by reference(index = 1u, required = false, dataModel = { MigrationCycleRightModel })
}

object MigrationCycleRightModel : RootDataModel<MigrationCycleRightModel>(
    name = "MigrationCycleRightModel",
    version = Version(1),
) {
    val leftRef by reference(index = 1u, required = false, dataModel = { MigrationCycleLeftModel })
}

class MigrationDependencyGraphTest {
    @Test
    fun ordersModelsByDependencies() {
        val ordered = orderMigrationModelIds(
            mapOf(
                2u to MigrationDependentModel,
                1u to MigrationBaseModel,
            )
        )

        assertEquals(listOf(1u, 2u), ordered)
    }

    @Test
    fun detectsCyclesAndReportsPath() {
        val exception = assertFailsWith<MigrationException> {
            orderMigrationModelIds(
                mapOf(
                    1u to MigrationCycleLeftModel,
                    2u to MigrationCycleRightModel,
                )
            )
        }

        val message = exception.message.orEmpty()
        assertTrue(message.contains("Dependency cycle detected"))
        assertTrue(message.contains("MigrationCycleLeftModel"))
        assertTrue(message.contains("MigrationCycleRightModel"))
        assertTrue(message.contains("->"))
    }
}
