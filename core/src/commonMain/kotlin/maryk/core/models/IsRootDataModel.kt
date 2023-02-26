package maryk.core.models

import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.models.migration.MigrationStatus.NewIndicesOnExistingProperties
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.types.Key
import maryk.core.properties.types.Version
import maryk.lib.synchronizedIteration

interface IsRootDataModel<P : IsValuesPropertyDefinitions> : IsValuesDataModel<P> {
    val keyDefinition: IsIndexable
    val indices: List<IsIndexable>?

    val version: Version

    val keyByteSize: Int
    val keyIndices: IntArray

    /** Get Key by [base64] bytes as string representation */
    fun key(base64: String): Key<*>

    /** Get Key by byte [reader] */
    fun key(reader: () -> Byte): Key<*>

    /** Get Key by [bytes] array */
    fun key(bytes: ByteArray): Key<*>

    val orderedIndices: List<IsIndexable>?

    override fun isMigrationNeeded(
        storedDataModel: IsDataModel<*>,
        migrationReasons: MutableList<String>
    ): MigrationStatus {
        val indicesToIndex = mutableListOf<IsIndexable>()
        if (storedDataModel is IsRootDataModel<*>) {
            // Only process indices if they are present on new model.
            // If they are present on stored but not on new, accept it.
            orderedIndices?.let { indices ->
                if (storedDataModel.orderedIndices == null) {
                    // Only index the values which have stored properties on the stored model
                    val toIndex = indices.filter { it.isCompatibleWithModel(storedDataModel) }
                    indicesToIndex.addAll(toIndex)
                } else {
                    synchronizedIteration(
                        indices.iterator(),
                        storedDataModel.orderedIndices!!.iterator(),
                        { newValue, storedValue ->
                            newValue.referenceStorageByteArray compareTo storedValue.referenceStorageByteArray
                        },
                        processOnlyOnIterator1 = { newIndex ->
                            // Only index the values which have stored properties on the stored model
                            if (newIndex.isCompatibleWithModel(storedDataModel)) {
                                indicesToIndex.add(newIndex)
                            }
                        }
                    )
                }
            }

            if (storedDataModel.version.major != this.version.major) {
                migrationReasons += "Major version was increased: ${storedDataModel.version} -> ${this.version}"
            }

            if (storedDataModel.keyDefinition !== this.keyDefinition) {
                migrationReasons += "Key definition was not the same"
            }
        } else {
            migrationReasons += "Stored model is not a root data model"
        }

        val parentResult = super.isMigrationNeeded(storedDataModel, migrationReasons)

        return if (indicesToIndex.isEmpty()) {
            parentResult
        } else when (parentResult) {
            is NeedsMigration -> NeedsMigration(storedDataModel, migrationReasons, indicesToIndex)
            else -> NewIndicesOnExistingProperties(indicesToIndex)
        }
    }
}
