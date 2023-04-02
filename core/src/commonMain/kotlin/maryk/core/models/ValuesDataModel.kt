package maryk.core.models

import maryk.core.models.definitions.IsRootDataModelDefinition
import maryk.core.models.definitions.IsValuesDataModelDefinition
import maryk.core.models.serializers.DataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.AnyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.Values
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.StartObject
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

/** A collection of Property Definitions which can be used to model a ObjectDataModel */
abstract class ValuesDataModel : AbstractDataModel<Any>(), IsValuesDataModel

internal class MutableRootDataModel : MutableValuesDataModel<MutableRootDataModel>(), IsRootDataModel {
    override val Serializer = DataModelSerializer<Any, Values<MutableRootDataModel>, MutableRootDataModel, IsPropertyContext>(this)
    override val Model: IsRootDataModelDefinition<MutableRootDataModel> get() = super.Model as IsRootDataModelDefinition<MutableRootDataModel>
}

internal class MutableDataModel : MutableValuesDataModel<MutableDataModel>(), IsDataModel {
    override val Serializer = DataModelSerializer<Any, Values<MutableDataModel>, MutableDataModel, IsPropertyContext>(this)
    override val Model: IsValuesDataModelDefinition<MutableDataModel> get() = super.Model
}

/** Mutable variant of DataModel for a IsCollectionDefinition implementation */
internal abstract class MutableValuesDataModel<DM: IsValuesDataModel> : TypedValuesDataModel<DM>(),
    IsMutableDataModel<AnyDefinitionWrapper> {
    internal var _model: IsValuesDataModelDefinition<DM>? = null

    override val Model: IsValuesDataModelDefinition<DM>
        get() = _model ?: throw Exception("No Model yet set, likely DataModel was not initialized yet")

    override fun add(element: AnyDefinitionWrapper): Boolean {
        this.addSingle(propertyDefinitionWrapper = element)
        return true
    }

    override fun addAll(elements: Collection<AnyDefinitionWrapper>): Boolean {
        elements.forEach {
            this.addSingle(it)
        }
        return true
    }

    override fun clear() {}
    override fun remove(element: AnyDefinitionWrapper) = false
    override fun removeAll(elements: Collection<AnyDefinitionWrapper>) = false
    override fun retainAll(elements: Collection<AnyDefinitionWrapper>) = false
}

/** Definition for a DataModel */
internal data class DataModelCollectionDefinition(
    val isRootModel: Boolean,
    override val capturer: Unit.(DefinitionsConversionContext?, ValuesDataModel) -> Unit
) : IsCollectionDefinition<
    AnyDefinitionWrapper,
        ValuesDataModel,
    DefinitionsConversionContext,
    EmbeddedObjectDefinition<
        AnyDefinitionWrapper,
            IsSimpleBaseObjectDataModel<AnyDefinitionWrapper, IsPropertyContext, IsPropertyContext>,
            IsPropertyContext,
            IsPropertyContext
    >
>, IsDataModelCollectionDefinition<ValuesDataModel> {
    override val required = true
    override val final = true
    override val minSize: UInt? = null
    override val maxSize: UInt? = null

    override val valueDefinition = EmbeddedObjectDefinition(
        dataModel = {
            @Suppress("UNCHECKED_CAST")
            IsDefinitionWrapper.Model as IsSimpleBaseObjectDataModel<AnyDefinitionWrapper, IsPropertyContext, IsPropertyContext>
        }
    )

    override fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<ValuesDataModel, IsPropertyDefinition<ValuesDataModel>, *>?,
        newValue: ValuesDataModel,
        validator: (item: AnyDefinitionWrapper, itemRefFactory: () -> IsPropertyReference<AnyDefinitionWrapper, IsPropertyDefinition<AnyDefinitionWrapper>, *>?) -> Any
    ) {}

    override fun newMutableCollection(context: DefinitionsConversionContext?): MutableValuesDataModel<*> =
        when (isRootModel) {
            true -> MutableRootDataModel()
            else -> MutableDataModel()
        }.apply {
            capturer(Unit, context, this)
        }

    /**
     * Overridden to render definitions list in YAML as objects
     */
    override fun writeJsonValue(
        value: ValuesDataModel,
        writer: IsJsonLikeWriter,
        context: DefinitionsConversionContext?
    ) {
        if (writer is YamlWriter) {
            writer.writeStartObject()
            for (it in value) {
                valueDefinition.writeJsonValue(it, writer, context)
            }
            writer.writeEndObject()
        } else {
            super.writeJsonValue(value, writer, context)
        }
    }

    override fun readJson(reader: IsJsonLikeReader, context: DefinitionsConversionContext?): ValuesDataModel {
        return if (reader is IsYamlReader) {
            if (reader.currentToken !is StartObject) {
                throw ParseException("Property definitions should be an Object")
            }
            val collection = newMutableCollection(context)

            while (reader.nextToken() !== EndObject) {
                collection.add(
                    valueDefinition.readJson(reader, context)
                )
            }
            collection
        } else {
            super.readJson(reader, context)
        }
    }
}

/** Wrapper specifically to wrap a DataModelCollectionDefinition */
internal data class DataModelCollectionDefinitionWrapper<in DO : Any>(
    override val index: UInt,
    override val name: String,
    override val definition: DataModelCollectionDefinition,
    override val getter: (DO) -> ValuesDataModel?,
    override val alternativeNames: Set<String>? = null
) :
    IsCollectionDefinition<AnyDefinitionWrapper, ValuesDataModel, DefinitionsConversionContext, EmbeddedObjectDefinition<AnyDefinitionWrapper, IsSimpleBaseObjectDataModel<AnyDefinitionWrapper, IsPropertyContext, IsPropertyContext>, IsPropertyContext, IsPropertyContext>> by definition,
    IsDefinitionWrapper<ValuesDataModel, ValuesDataModel, DefinitionsConversionContext, DO>
{
    override val graphType = PropRef

    override val toSerializable: (Unit.(ValuesDataModel?, DefinitionsConversionContext?) -> ValuesDataModel?)? = null
    override val fromSerializable: (Unit.(ValuesDataModel?) -> ValuesDataModel?)? = null
    override val shouldSerialize: (Unit.(Any) -> Boolean)? = null
    override val capturer: (Unit.(DefinitionsConversionContext, ValuesDataModel) -> Unit)? = null

    override fun ref(parentRef: AnyPropertyReference?) = throw NotImplementedError()
}
