package maryk.core.models.migration

import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.models.migration.MigrationStatus.NewIndicesOnExistingProperties
import maryk.core.models.migration.MigrationStatus.OnlySafeAdds
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.index.UUIDv4Key
import maryk.core.properties.references.IsFixedBytesPropertyReference
import maryk.core.properties.definitions.string
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
import kotlin.uuid.Uuid

private object StoredDeclarationOrderModel : RootDataModel<StoredDeclarationOrderModel>(name = "DeclarationOrderModel") {
    val first by string(index = 1u)
    val second by boolean(index = 2u)
}

private object ReorderedDeclarationOrderModel : RootDataModel<ReorderedDeclarationOrderModel>(name = "DeclarationOrderModel") {
    val second by boolean(index = 2u)
    val first by string(index = 1u)
}

private object ReorderedChangedDeclarationOrderModel : RootDataModel<ReorderedChangedDeclarationOrderModel>(name = "DeclarationOrderModel") {
    val second by boolean(index = 2u)
    val first by boolean(index = 1u)
}

private class IdentityEqualityKey : IsFixedBytesPropertyReference<Uuid> by UUIDv4Key

private object StoredIdentityEqualityKeyModel : RootDataModel<StoredIdentityEqualityKeyModel>(
    name = "IdentityEqualityKeyModel",
    keyDefinition = { IdentityEqualityKey() },
)

private object CurrentIdentityEqualityKeyModel : RootDataModel<CurrentIdentityEqualityKeyModel>(
    name = "IdentityEqualityKeyModel",
    keyDefinition = { IdentityEqualityKey() },
)

class IsMigrationNeededTest {
    @Test
    fun migrationIsNotNeeded() {
        assertEquals(
            UpToDate,
            ModelV1.isMigrationNeeded(ModelV1)
        )
    }

    @Test
    fun reorderingPropertiesWithStableIndicesDoesNotNeedMigration() {
        assertEquals(
            UpToDate,
            ReorderedDeclarationOrderModel.isMigrationNeeded(StoredDeclarationOrderModel)
        )
    }

    @Test
    fun reorderingPropertiesWithChangedDefinitionNeedsMigration() {
        assertIs<NeedsMigration>(
            ReorderedChangedDeclarationOrderModel.isMigrationNeeded(StoredDeclarationOrderModel)
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
    fun equalKeyEncodingsDoNotNeedMigrationWhenKeyObjectsDiffer() {
        assertEquals(
            UpToDate,
            CurrentIdentityEqualityKeyModel.isMigrationNeeded(StoredIdentityEqualityKeyModel)
        )
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
            indexesToIndex.containsAll(listOf(ModelV2ExtraIndex { newNumber::ref }))
        }
    }

    @Test
    fun noNewIndexAddedOnExistingProperties() {
        assertIs<NeedsMigration>(ModelV2ExtraIndex.isMigrationNeeded(ModelV1)).apply {
            assertNull(indexesToIndex)
        }
    }
}
