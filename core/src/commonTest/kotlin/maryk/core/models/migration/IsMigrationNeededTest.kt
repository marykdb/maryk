package maryk.core.models.migration

import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.models.migration.MigrationStatus.NewIndicesOnExistingProperties
import maryk.core.models.migration.MigrationStatus.OnlySafeAdds
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.test.models.ModelMissingProperty
import maryk.test.models.ModelV1
import maryk.test.models.ModelV1_1
import maryk.test.models.ModelV1_1WrongKey
import maryk.test.models.ModelV2
import maryk.test.models.ModelV2ExtraIndex
import maryk.test.models.ModelV2ReservedNamesAndIndices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class IsMigrationNeededTest {
    @Test
    fun migrationIsNotNeeded() {
        assertEquals(
            UpToDate,
            ModelV1.isMigrationNeeded(ModelV1)
        )
    }

    @Test
    fun migrationIsNeededWithVersion() {
        assertIs<NeedsMigration>(ModelV2.isMigrationNeeded(ModelV1))
    }

    @Test
    fun migrationIsNeededForWrongKey() {
        assertIs<NeedsMigration>(ModelV1_1WrongKey.isMigrationNeeded(ModelV1))
    }

    @Test
    fun missingProperty() {
        assertIs<NeedsMigration>(ModelMissingProperty.isMigrationNeeded(ModelV1))
    }

    @Test
    fun reservedIndexAndName() {
        assertIs<OnlySafeAdds>(ModelV2ReservedNamesAndIndices.isMigrationNeeded(ModelV1))
    }

    @Test
    fun onlySafeAdditions() {
        assertIs<OnlySafeAdds>(ModelV1_1.isMigrationNeeded(ModelV1))
    }

    @Test
    fun newIndexAddedOnExistingProperties() {
        assertIs<NewIndicesOnExistingProperties>(ModelV2ExtraIndex.isMigrationNeeded(ModelV2)).apply {
            indicesToIndex.containsAll(listOf(ModelV2ExtraIndex { newNumber::ref }))
        }
    }

    @Test
    fun noNewIndexAddedOnExistingProperties() {
        assertIs<NeedsMigration>(ModelV2ExtraIndex.isMigrationNeeded(ModelV1)).apply {
            assertNull(indicesToIndex)
        }
    }
}
