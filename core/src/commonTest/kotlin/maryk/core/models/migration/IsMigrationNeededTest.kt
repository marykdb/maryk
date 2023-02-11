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
            ModelV1.Model.isMigrationNeeded(ModelV1.Model)
        )
    }

    @Test
    fun migrationIsNeededWithVersion() {
        assertIs<NeedsMigration>(ModelV2.Model.isMigrationNeeded(ModelV1.Model))
    }

    @Test
    fun migrationIsNeededForWrongKey() {
        assertIs<NeedsMigration>(ModelV1_1WrongKey.Model.isMigrationNeeded(ModelV1.Model))
    }

    @Test
    fun missingProperty() {
        assertIs<NeedsMigration>(ModelMissingProperty.Model.isMigrationNeeded(ModelV1.Model))
    }

    @Test
    fun reservedIndexAndName() {
        assertIs<OnlySafeAdds>(ModelV2ReservedNamesAndIndices.Model.isMigrationNeeded(ModelV1.Model))
    }

    @Test
    fun onlySafeAdditions() {
        assertIs<OnlySafeAdds>(ModelV1_1.Model.isMigrationNeeded(ModelV1.Model))
    }

    @Test
    fun newIndexAddedOnExistingProperties() {
        assertIs<NewIndicesOnExistingProperties>(ModelV2ExtraIndex.Model.isMigrationNeeded(ModelV2.Model)).apply {
            indicesToIndex.containsAll(listOf(ModelV2ExtraIndex { newNumber::ref }))
        }
    }

    @Test
    fun noNewIndexAddedOnExistingProperties() {
        assertIs<NeedsMigration>(ModelV2ExtraIndex.Model.isMigrationNeeded(ModelV1.Model)).apply {
            assertNull(indicesToIndex)
        }
    }
}
