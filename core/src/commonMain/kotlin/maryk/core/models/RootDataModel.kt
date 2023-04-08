package maryk.core.models

import maryk.core.models.definitions.RootDataModelDefinition
import maryk.core.models.migration.MigrationStatus
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Version
import maryk.lib.synchronizedIteration

open class RootDataModel<DM: IsValuesDataModel>(
    keyDefinition: () -> IsIndexable = { UUIDKey },
    version: Version = Version(1),
    indices: (() -> List<IsIndexable>)? = null,
    reservedIndices: List<UInt>? = null,
    reservedNames: List<String>? = null,
    name: String? = null,
) : TypedValuesDataModel<DM>(), IsRootDataModel {
    @Suppress("UNCHECKED_CAST", "LeakingThis")
    private val typedThis: DM = this as DM

    override val Meta: RootDataModelDefinition<DM> by lazy {
        RootDataModelDefinition<DM>(
            keyDefinition = keyDefinition.invoke(),
            version = version,
            indices = indices?.invoke(),
            reservedIndices = reservedIndices,
            reservedNames = reservedNames,
            name = name ?: this::class.simpleName!!,
            properties = typedThis,
        )
    }

    operator fun <T : Any, R : IsPropertyReference<T, IsPropertyDefinition<T>, *>> invoke(
        parent: AnyOutPropertyReference? = null,
        referenceGetter: DM.() -> (AnyOutPropertyReference?) -> R
    ) = referenceGetter(typedThis)(parent)

    override fun isMigrationNeeded(
        storedDataModel: IsStorableDataModel,
        migrationReasons: MutableList<String>
    ): MigrationStatus {
        val indicesToIndex = mutableListOf<IsIndexable>()
        if (storedDataModel is IsRootDataModel) {
            // Only process indices if they are present on new model.
            // If they are present on stored but not on new, accept it.
            Meta.orderedIndices?.let { indices ->
                if (storedDataModel.Meta.orderedIndices == null) {
                    // Only index the values which have stored properties on the stored model
                    val toIndex = indices.filter { it.isCompatibleWithModel(storedDataModel) }
                    indicesToIndex.addAll(toIndex)
                } else {
                    synchronizedIteration(
                        indices.iterator(),
                        storedDataModel.Meta.orderedIndices!!.iterator(),
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

            if (storedDataModel.Meta.version.major != this.Meta.version.major) {
                migrationReasons += "Major version was increased: ${storedDataModel.Meta.version} -> ${this.Meta.version}"
            }

            if (storedDataModel.Meta.keyDefinition !== this.Meta.keyDefinition) {
                migrationReasons += "Key definition was not the same"
            }
        } else {
            migrationReasons += "Stored model is not a root data model"
        }

        val parentResult = super<TypedValuesDataModel>.isMigrationNeeded(storedDataModel, migrationReasons)

        return if (indicesToIndex.isEmpty()) {
            parentResult
        } else when (parentResult) {
            is MigrationStatus.NeedsMigration -> MigrationStatus.NeedsMigration(
                storedDataModel,
                migrationReasons,
                indicesToIndex
            )
            else -> MigrationStatus.NewIndicesOnExistingProperties(indicesToIndex)
        }
    }
}
