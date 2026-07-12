package maryk.datastore.shared

import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private object RegistryModel : RootDataModel<RegistryModel>(name = "RegistryModel")
private object DuplicateRegistryModelA : RootDataModel<DuplicateRegistryModelA>(name = "DuplicateRegistryModel")
private object DuplicateRegistryModelB : RootDataModel<DuplicateRegistryModelB>(name = "DuplicateRegistryModel")
private object BlankRegistryModel : RootDataModel<BlankRegistryModel>(name = "")
private object AddedRegistryModel : RootDataModel<AddedRegistryModel>(name = "AddedRegistryModel")

class DataModelRegistryTest {
    @Test
    fun rejectsReservedModelId() {
        val exception = assertFailsWith<StorageException> {
            validatedDataModelRegistry(mapOf(0u to RegistryModel))
        }

        assertTrue(exception.message.orEmpty().contains("Model ID 0"))
    }

    @Test
    fun rejectsBlankModelName() {
        val exception = assertFailsWith<StorageException> {
            validatedDataModelRegistry(mapOf(1u to BlankRegistryModel))
        }

        assertTrue(exception.message.orEmpty().contains("blank name"))
        assertTrue(exception.message.orEmpty().contains("1"))
    }

    @Test
    fun rejectsDuplicateModelNames() {
        val exception = assertFailsWith<StorageException> {
            validatedDataModelRegistry(
                mapOf(
                    1u to DuplicateRegistryModelA,
                    2u to DuplicateRegistryModelB,
                )
            )
        }

        assertTrue(exception.message.orEmpty().contains("DuplicateRegistryModel"))
        assertTrue(exception.message.orEmpty().contains("1"))
        assertTrue(exception.message.orEmpty().contains("2"))
    }

    @Test
    fun snapshotsCallerOwnedMap() {
        val callerModels = mutableMapOf<UInt, IsRootDataModel>(1u to RegistryModel)
        val registry = validatedDataModelRegistry(callerModels)

        callerModels.clear()
        callerModels[2u] = AddedRegistryModel

        assertEquals(mapOf(1u to RegistryModel), registry.dataModelsById)
        assertEquals(mapOf("RegistryModel" to 1u), registry.dataModelIdsByString)
        assertFalse(registry.dataModelsById.containsKey(2u))
        assertFalse(registry.dataModelIdsByString.containsKey("AddedRegistryModel"))
    }
}
