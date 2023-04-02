package maryk.core.properties

import maryk.core.extensions.bytes.initByteArray
import maryk.core.models.definitions.IsRootDataModelDefinition
import maryk.core.models.definitions.RootDataModelDefinition
import maryk.core.models.migration.MigrationStatus
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.graph.IsPropRefGraphNode
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.IsFixedBytesPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.properties.types.Version
import maryk.core.query.RequestContext
import maryk.core.query.changes.IsChange
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItems
import maryk.core.values.Values
import maryk.lib.exceptions.ParseException
import maryk.lib.synchronizedIteration
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface IsRootModel: IsValuesPropertyDefinitions {
    override val Model: IsRootDataModelDefinition<out IsValuesPropertyDefinitions>
}

open class RootModel<DM: IsValuesPropertyDefinitions>(
    keyDefinition: () -> IsIndexable = { UUIDKey },
    version: Version = Version(1),
    indices: (() -> List<IsIndexable>)? = null,
    reservedIndices: List<UInt>? = null,
    reservedNames: List<String>? = null,
    name: String? = null,
) : TypedValuesModel<RootDataModelDefinition<DM>, DM>(), IsRootModel {
    @Suppress("UNCHECKED_CAST")
    override val Model: RootDataModelDefinition<DM> by lazy {
        RootDataModelDefinition(
            keyDefinition = keyDefinition.invoke(),
            version = version,
            indices = indices?.invoke(),
            reservedIndices = reservedIndices,
            reservedNames = reservedNames,
            name = name ?: this::class.simpleName!!,
            properties = this,
        ) as RootDataModelDefinition<DM>
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any, R : IsPropertyReference<T, IsPropertyDefinition<T>, *>> invoke(
        parent: AnyOutPropertyReference? = null,
        referenceGetter: DM.() -> (AnyOutPropertyReference?) -> R
    ) = referenceGetter(this as DM)(parent)

    override fun isMigrationNeeded(
        storedDataModel: IsStorableModel,
        migrationReasons: MutableList<String>
    ): MigrationStatus {
        val indicesToIndex = mutableListOf<IsIndexable>()
        if (storedDataModel is IsRootModel) {
            // Only process indices if they are present on new model.
            // If they are present on stored but not on new, accept it.
            Model.orderedIndices?.let { indices ->
                if (storedDataModel.Model.orderedIndices == null) {
                    // Only index the values which have stored properties on the stored model
                    val toIndex = indices.filter { it.isCompatibleWithModel(storedDataModel) }
                    indicesToIndex.addAll(toIndex)
                } else {
                    synchronizedIteration(
                        indices.iterator(),
                        storedDataModel.Model.orderedIndices!!.iterator(),
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

            if (storedDataModel.Model.version.major != this.Model.version.major) {
                migrationReasons += "Major version was increased: ${storedDataModel.Model.version} -> ${this.Model.version}"
            }

            if (storedDataModel.Model.keyDefinition !== this.Model.keyDefinition) {
                migrationReasons += "Key definition was not the same"
            }
        } else {
            migrationReasons += "Stored model is not a root data model"
        }

        val parentResult = super<TypedValuesModel>.isMigrationNeeded(storedDataModel, migrationReasons)

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

/** Create a Values object with given [changes] */
fun <DM : IsRootModel> DM.fromChanges(
    context: RequestContext?,
    changes: List<IsChange>
) = if (changes.isEmpty()) {
    Values(this, ValueItems(), context)
} else {
    val valueItemsToChange = MutableValueItems(mutableListOf())

    for (change in changes) {
        change.changeValues { ref, valueChanger ->
            valueItemsToChange.copyFromOriginalAndChange(null, ref.index, valueChanger)
        }
    }

    Values(this, valueItemsToChange, context)
}


@OptIn(ExperimentalEncodingApi::class)
fun <DM: IsRootModel> DM.key(base64: String) = key(Base64.Mime.decode(base64))

fun <DM: IsRootModel> DM.key(reader: () -> Byte) = Key<DM>(
    initByteArray(Model.keyByteSize, reader)
)

fun <DM: IsRootModel> DM.key(bytes: ByteArray): Key<DM> {
    if (bytes.size != Model.keyByteSize) {
        throw ParseException("Invalid byte length for key. Expected ${ Model.keyByteSize } instead of ${bytes.size}")
    }
    return Key(bytes)
}

/**
 * Create Property reference graph with list of graphables that are generated with [runner] on Properties
 * The graphables are sorted after generation so the RootPropRefGraph can be processed quicker.
 */
fun <DM : IsRootModel> DM.graph(
    runner: DM.() -> List<IsPropRefGraphNode<DM>>
) = RootPropRefGraph(runner(this).sortedBy { it.index })

/** Get Key based on [values] */
fun <DM : IsRootModel> DM.key(values: Values<DM>): Key<DM> {
    val bytes = ByteArray(this.Model.keyByteSize)
    var index = 0
    when (val keyDef = this.Model.keyDefinition) {
        is Multiple -> {
            keyDef.writeStorageBytes(values) {
                bytes[index++] = it
            }
        }
        is IsFixedBytesPropertyReference<out Any> -> {
            val value = keyDef.getValue(values)

            @Suppress("UNCHECKED_CAST")
            (keyDef as IsFixedStorageBytesEncodable<Any>).writeStorageBytes(value) {
                bytes[index++] = it
            }
        }
    }

    return Key(bytes)
}
