package maryk.core.query.responses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.types.TypedValue
import maryk.core.query.requests.UpdateHistoryCursor
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.UpdateResponseType
import maryk.core.query.responses.updates.mapOfUpdateResponses
import maryk.core.values.SimpleObjectValues

/** Response with [updates] to [dataModel] */
class UpdatesResponse<DM : IsRootDataModel>(
    override val dataModel: DM,
    val updates: List<IsUpdateResponse<DM>>,
    val dataFetchType: DataFetchType? = null,
) : IsDataResponse<DM> {
    private var continuationCursor: UpdateHistoryCursor? = null
    val nextCursor: UpdateHistoryCursor? get() = continuationCursor

    constructor(
        dataModel: DM,
        updates: List<IsUpdateResponse<DM>>,
        dataFetchType: DataFetchType?,
        nextCursor: UpdateHistoryCursor?,
    ) : this(dataModel, updates, dataFetchType) {
        continuationCursor = nextCursor
    }

    // Preserve the existing JVM data-class signatures while copying the new cursor too.
    fun copy(
        dataModel: DM = this.dataModel,
        updates: List<IsUpdateResponse<DM>> = this.updates,
        dataFetchType: DataFetchType? = this.dataFetchType,
    ) = UpdatesResponse(dataModel, updates, dataFetchType, nextCursor)

    fun copy(
        nextCursor: UpdateHistoryCursor?,
        dataModel: DM = this.dataModel,
        updates: List<IsUpdateResponse<DM>> = this.updates,
        dataFetchType: DataFetchType? = this.dataFetchType,
    ) = UpdatesResponse(dataModel, updates, dataFetchType, nextCursor)

    operator fun component1() = dataModel
    operator fun component2() = updates
    operator fun component3() = dataFetchType

    override fun equals(other: Any?) = other is UpdatesResponse<*> &&
        dataModel == other.dataModel && updates == other.updates &&
        dataFetchType == other.dataFetchType && nextCursor == other.nextCursor

    override fun hashCode(): Int {
        var result = dataModel.hashCode()
        result = 31 * result + updates.hashCode()
        result = 31 * result + (dataFetchType?.hashCode() ?: 0)
        return 31 * result + (nextCursor?.hashCode() ?: 0)
    }

    override fun toString() = "UpdatesResponse(dataModel=$dataModel, updates=$updates, dataFetchType=$dataFetchType, nextCursor=$nextCursor)"

    companion object : SimpleQueryModel<UpdatesResponse<*>>() {
        val dataModel by addDataModel({ it.dataModel })
        val updates by list(
            index = 2u,
            getter = UpdatesResponse<*>::updates,
            default = emptyList(),
            valueDefinition = InternalMultiTypeDefinition(
                typeEnum = UpdateResponseType,
                definitionMap = mapOfUpdateResponses
            ),
            toSerializable = { TypedValue(it.type, it) },
            fromSerializable = { it.value }
        )
        internal val dataFetchType = MultiTypeDefinitionWrapper(
            3u,
            "dataFetchType",
            MultiTypeDefinition(required = false, typeEnum = DataFetchTypeType),
            getter = UpdatesResponse<*>::dataFetchType,
            toSerializable = { value, _ -> value?.let { TypedValue(it.type, it) } },
            fromSerializable = { it?.value },
        ).also(::addSingle)

        val nextCursor by embedObject(4u, UpdatesResponse<*>::nextCursor, dataModel = { UpdateHistoryCursor.Model })

        override fun invoke(values: SimpleObjectValues<UpdatesResponse<*>>) = UpdatesResponse(
            dataModel = values(1u),
            updates = values(2u),
            dataFetchType = values(dataFetchType.index),
            nextCursor = values(nextCursor.index),
        )
    }
}
