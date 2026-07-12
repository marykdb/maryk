@file:Suppress("unused")

package maryk.core.models.migration

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.embed
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
    val base by embed(index = 1u, required = false, dataModel = { MigrationBaseModel })
}

object MigrationReferenceLeftModel : RootDataModel<MigrationReferenceLeftModel>(
    name = "MigrationReferenceLeftModel",
    version = Version(1),
) {
    val rightRef by reference(index = 1u, required = false, dataModel = { MigrationReferenceRightModel })
}

object MigrationReferenceRightModel : RootDataModel<MigrationReferenceRightModel>(
    name = "MigrationReferenceRightModel",
    version = Version(1),
) {
    val leftRef by reference(index = 1u, required = false, dataModel = { MigrationReferenceLeftModel })
}

object MigrationSelfReferenceModel : RootDataModel<MigrationSelfReferenceModel>(
    name = "MigrationSelfReferenceModel",
    version = Version(1),
) {
    val parent by reference(index = 1u, required = false, dataModel = { MigrationSelfReferenceModel })
}

object MigrationUserModel : RootDataModel<MigrationUserModel>(
    name = "MigrationUserModel",
    version = Version(1),
) {
    val ownedParticipant by reference(index = 1u, required = false, dataModel = { MigrationParticipantModel })
}

object MigrationUserActionModel : DataModel<MigrationUserActionModel>() {
    val user by reference(index = 1u, required = false, dataModel = { MigrationUserModel })
    val value by string(index = 2u, required = false)
}

object MigrationParticipantModel : RootDataModel<MigrationParticipantModel>(
    name = "MigrationParticipantModel",
    version = Version(1),
) {
    val lastEdit by embed(index = 1u, required = false, dataModel = { MigrationUserActionModel })
}

object MigrationStructuralCycleLeftModel : RootDataModel<MigrationStructuralCycleLeftModel>(
    name = "MigrationStructuralCycleLeftModel",
    version = Version(1),
) {
    val right by embed(index = 1u, required = false, dataModel = { MigrationStructuralCycleRightModel })
}

object MigrationStructuralCycleRightModel : RootDataModel<MigrationStructuralCycleRightModel>(
    name = "MigrationStructuralCycleRightModel",
    version = Version(1),
) {
    val left by embed(index = 1u, required = false, dataModel = { MigrationStructuralCycleLeftModel })
}

object MigrationDuplicateNameModelA : RootDataModel<MigrationDuplicateNameModelA>(
    name = "MigrationDuplicateNameModel",
)

object MigrationDuplicateNameModelB : RootDataModel<MigrationDuplicateNameModelB>(
    name = "MigrationDuplicateNameModel",
)

object MigrationBlankNameModel : RootDataModel<MigrationBlankNameModel>(name = "")

class MigrationDependencyGraphTest {
    @Test
    fun rejectsBlankModelNames() {
        val exception = assertFailsWith<MigrationException> {
            orderMigrationModelIds(mapOf(7u to MigrationBlankNameModel))
        }

        assertTrue(exception.message.orEmpty().contains("blank name"))
        assertTrue(exception.message.orEmpty().contains("7"))
    }

    @Test
    fun rejectsDuplicateModelNames() {
        val exception = assertFailsWith<MigrationException> {
            orderMigrationModelIds(
                mapOf(
                    3u to MigrationDuplicateNameModelA,
                    9u to MigrationDuplicateNameModelB,
                )
            )
        }

        assertTrue(exception.message.orEmpty().contains("MigrationDuplicateNameModel"))
        assertTrue(exception.message.orEmpty().contains("3"))
        assertTrue(exception.message.orEmpty().contains("9"))
    }

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
    fun ignoresNestedRootReferencesForMigrationOrdering() {
        val ordered = orderMigrationModelIds(
            mapOf(
                1u to MigrationUserModel,
                2u to MigrationParticipantModel,
            )
        )

        assertEquals(setOf(1u, 2u), ordered.toSet())
        assertEquals(2, ordered.size)
    }

    @Test
    fun ignoresBidirectionalRootReferencesForMigrationOrdering() {
        val ordered = orderMigrationModelIds(
            mapOf(
                1u to MigrationReferenceLeftModel,
                2u to MigrationReferenceRightModel,
            )
        )

        assertEquals(setOf(1u, 2u), ordered.toSet())
        assertEquals(2, ordered.size)
    }

    @Test
    fun ignoresSelfReferenceForMigrationOrdering() {
        assertEquals(
            listOf(1u),
            orderMigrationModelIds(mapOf(1u to MigrationSelfReferenceModel))
        )
    }

    @Test
    fun detectsStructuralCyclesAndReportsPath() {
        val exception = assertFailsWith<MigrationException> {
            orderMigrationModelIds(
                mapOf(
                    1u to MigrationStructuralCycleLeftModel,
                    2u to MigrationStructuralCycleRightModel,
                )
            )
        }

        val message = exception.message.orEmpty()
        assertTrue(message.contains("Dependency cycle detected"))
        assertTrue(message.contains("MigrationStructuralCycleLeftModel"))
        assertTrue(message.contains("MigrationStructuralCycleRightModel"))
        assertTrue(message.contains("->"))
    }
}
