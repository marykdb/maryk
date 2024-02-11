package maryk.core.models

import maryk.core.definitions.MarykPrimitive
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.models.definitions.RootDataModelDefinition
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertiesCollectionDefinition
import maryk.core.properties.PropertiesCollectionDefinitionWrapper
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.definitions.wrapper.AnyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.EmbeddedObjectDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Version
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItem
import maryk.core.values.ValueItems
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.json.PresetJsonTokenReader
import maryk.lib.exceptions.ParseException
import maryk.lib.synchronizedIteration
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

/**
 * Base class for all DataModels which are at the root and can be stored into a DataStore.
 */
open class RootDataModel<DM: IsValuesDataModel> internal constructor(
    meta: (String?) -> RootDataModelDefinition,
) : TypedValuesDataModel<DM>(), IsRootDataModel, MarykPrimitive {
    constructor(
        keyDefinition: () -> IsIndexable = { UUIDKey },
        version: Version = Version(1),
        indices: (() -> List<IsIndexable>)? = null,
        reservedIndices: List<UInt>? = null,
        reservedNames: List<String>? = null,
        name: String? = null,
    ) : this({ passedName ->
        RootDataModelDefinition(
            name = name ?: passedName ?: throw DefNotFoundException("RootDataModel should have a name. Please define in a class instead of anonymous object or pass name as property."),
            keyDefinition = keyDefinition.invoke(),
            version = version,
            indices = indices?.invoke(),
            reservedIndices = reservedIndices,
            reservedNames = reservedNames,
        )
    })

    @Suppress("UNCHECKED_CAST", "LeakingThis")
    private val typedThis: DM = this as DM

    final override val Meta: RootDataModelDefinition by lazy { meta(this::class.simpleName) }

    operator fun <T : Any, R : IsPropertyReference<T, IsPropertyDefinition<T>, *>> invoke(
        parent: AnyOutPropertyReference? = null,
        referenceGetter: DM.() -> (AnyOutPropertyReference?) -> R
    ) = referenceGetter(typedThis)(parent)

    override fun equals(other: Any?) =
        super.equals(other) && other is RootDataModel<*> && this.Meta == other.Meta

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + Meta.hashCode()
        return result
    }

    override fun isMigrationNeeded(
        storedDataModel: IsStorableDataModel<*>,
        checkedDataModelNames: MutableList<String>?,
        migrationReasons: MutableList<String>,
    ): MigrationStatus {
        val indicesToIndex = mutableListOf<IsIndexable>()
        if (storedDataModel is IsRootDataModel) {
            // Only process indices if they are present on new model.
            // If they are present on stored but not on new, accept it.
            Meta.indices?.let { indices ->
                val orderedIndices = indices.sortedBy { it.referenceStorageByteArray }

                if (storedDataModel.Meta.indices == null) {
                    // Only index the values which have stored properties on the stored model
                    val toIndex = orderedIndices.filter { it.isCompatibleWithModel(storedDataModel) }
                    indicesToIndex.addAll(toIndex)
                } else {
                    val storedOrderedIndices = storedDataModel.Meta.indices!!.sortedBy { it.referenceStorageByteArray }

                    synchronizedIteration(
                        orderedIndices.iterator(),
                        storedOrderedIndices.iterator(),
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

        val parentResult = super<TypedValuesDataModel>.isMigrationNeeded(
            storedDataModel = storedDataModel,
            checkedDataModelNames = checkedDataModelNames,
            migrationReasons = migrationReasons
        )

        return if (parentResult == MigrationStatus.AlreadyProcessed) {
            return parentResult
        } else if (indicesToIndex.isEmpty()) {
            parentResult
        } else when (parentResult) {
            is MigrationStatus.NeedsMigration -> MigrationStatus.NeedsMigration(
                storedDataModel,
                migrationReasons,
                indicesToIndex
            )
            else -> MigrationStatus.NewIndicesOnExistingProperties(storedDataModel, indicesToIndex)
        }
    }

    object Model: DefinitionModel<RootDataModel<*>>() {
        val properties = PropertiesCollectionDefinitionWrapper<RootDataModel<*>>(
            1u,
            "properties",
            PropertiesCollectionDefinition(
                capturer = { context, propDefs ->
                    context?.apply {
                        this.propertyDefinitions = propDefs
                    } ?: throw ContextNotFoundException()
                }
            ),
            getter = {
                @Suppress("UNCHECKED_CAST")
                it as IsTypedDataModel<RootDataModel<*>>
            }
        ).also(this::addSingle)
        val meta = EmbeddedObjectDefinitionWrapper(
            2u,
            "meta",
            EmbeddedObjectDefinition(required = true, final = true, dataModel = { RootDataModelDefinition.Model }),
            getter = RootDataModel<*>::Meta,
        ).also(this::addSingle)

        override fun invoke(values: ObjectValues<RootDataModel<*>, IsObjectDataModel<RootDataModel<*>>>): RootDataModel<*> =
            RootDataModel<IsValuesDataModel>(meta = { values(meta.index) }).also {
                values<Collection<AnyDefinitionWrapper>>(properties.index).forEach(it::addSingle)
            }

        override val Serializer = object: ObjectDataModelSerializer<RootDataModel<*>, IsObjectDataModel<RootDataModel<*>>, ContainsDefinitionsContext, ContainsDefinitionsContext>(this) {
            override fun writeObjectAsJson(
                obj: RootDataModel<*>,
                writer: IsJsonLikeWriter,
                context: ContainsDefinitionsContext?,
                skip: List<IsDefinitionWrapper<*, *, *, RootDataModel<*>>>?
            ) {
                writer.writeStartObject()
                for (def in meta.dataModel) {
                    // Skip name if defined higher
                    if (def == meta.dataModel.name && context != null && context.currentDefinitionName == obj.Meta.name) {
                        context.currentDefinitionName = "" // Reset after use
                        continue
                    }
                    val value = def.getPropertyAndSerialize(obj.Meta, context) ?: continue
                    writer.writeFieldName(def.name)
                    def.writeJsonValue(value, writer, context)
                }
                if (writer is YamlWriter) {
                    // Write optimized format when writing yaml
                    for (property in obj) {
                        properties.valueDefinition.writeJsonValue(property, writer, context)
                    }
                } else {
                    @Suppress("UNCHECKED_CAST")
                    this.writeJsonValue(
                        properties as IsDefinitionWrapper<in Any, in Any, IsPropertyContext, IsRootDataModel>,
                        writer,
                        obj,
                        context
                    )
                }
                writer.writeEndObject()
            }

            override fun walkJsonToRead(
                reader: IsJsonLikeReader,
                values: MutableValueItems,
                context: ContainsDefinitionsContext?
            ) {
                val deserializedProperties = properties.newMutableCollection(context as? DefinitionsConversionContext)
                val metaValues = mutableListOf<ValueItem>()

                var keyDefinitionToReadLater: List<JsonToken>? = null
                var indicesToReadLater: List<JsonToken>? = null

                // Inject name if it was defined as a map key in a higher level
                context?.currentDefinitionName?.let { name ->
                    if (name.isNotBlank()) {
                        if (values.contains(RootDataModelDefinition.Model.name.index)) {
                            throw RequestException("Name $name was already defined by map")
                        }
                        // Reset it so no deeper value can reuse it
                        context.currentDefinitionName = ""

                        metaValues += ValueItem(RootDataModelDefinition.Model.name.index, name)
                    }
                }

                walker@ do {
                    val token = reader.currentToken

                    when (token) {
                        is JsonToken.StartComplexFieldName -> {
                            deserializedProperties += properties.valueDefinition.readJson(reader, context)
                        }
                        is JsonToken.FieldName -> {
                            val value = token.value ?: throw ParseException("Empty field name not allowed in JSON")

                            when (val definition = RootDataModelDefinition.Model[value]) {
                                null -> {
                                    if (value == properties.name) {
                                        reader.nextToken() // continue for field name
                                        deserializedProperties += properties.readJson(reader, context as DefinitionsConversionContext)
                                    } else {
                                        reader.skipUntilNextField()
                                        continue@walker
                                    }
                                }
                                RootDataModelDefinition.Model.indices -> {
                                    indicesToReadLater = mutableListOf<JsonToken>().apply {
                                        reader.skipUntilNextField(::add)
                                    }
                                    continue@walker
                                }
                                RootDataModelDefinition.Model.key -> {
                                    keyDefinitionToReadLater = mutableListOf<JsonToken>().apply {
                                        reader.skipUntilNextField(::add)
                                    }
                                    continue@walker
                                }
                                else -> {
                                    reader.nextToken()
                                    metaValues += ValueItem(definition.index, definition.definition.readJson(reader, context))
                                }
                            }
                        }
                        else -> break@walker
                    }
                    reader.nextToken()
                } while (token !is JsonToken.Stopped)

                fun readDelayed(
                    tokensToReadLater: List<JsonToken>?,
                    propertyDefinitionWrapper: IsDefinitionWrapper<*, *, DefinitionsConversionContext, *>,
                ) {
                    tokensToReadLater?.let { jsonTokens ->
                        val lateReader = if (reader is IsYamlReader) {
                            jsonTokens.map { reader.pushToken(it) }
                            reader.pushToken(reader.currentToken)
                            reader.nextToken()
                            reader
                        } else {
                            PresetJsonTokenReader(jsonTokens)
                        }

                        metaValues += ValueItem(
                            propertyDefinitionWrapper.index,
                            propertyDefinitionWrapper.readJson(lateReader, context as DefinitionsConversionContext?)
                        )

                        if (reader is IsYamlReader) {
                            reader.nextToken()
                        }
                    }
                }

                (context as? DefinitionsConversionContext)?.let {
                    it.propertyDefinitions = deserializedProperties as IsDataModel?
                }

                readDelayed(keyDefinitionToReadLater, RootDataModelDefinition.Model.key)
                readDelayed(indicesToReadLater, RootDataModelDefinition.Model.indices)

                metaValues.sortBy { it.index }

                if (model.isNotEmpty()) {
                    values[properties.index] = deserializedProperties
                }
                if (metaValues.isNotEmpty()) {
                    values[meta.index] = ObjectValues(
                        RootDataModelDefinition.Model,
                        ValueItems(*metaValues.toTypedArray()),
                    ).toDataObject()
                }
            }
        }
    }
}
