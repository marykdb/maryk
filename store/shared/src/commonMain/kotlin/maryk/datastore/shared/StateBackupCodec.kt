package maryk.datastore.shared

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData

/** The state format uses the existing contextual, portable record encoding. */
internal class StateBackupCodec(private val models: Map<String, IsRootDataModel>) {
    private fun context(name: String, model: IsRootDataModel = models.getValue(name)) = RequestContext(
        DefinitionsContext((models + (name to model)).mapValues { DataModelReference(it.value) }.toMutableMap()), model,
    ).also { it.rejectUnknownProtoBufFields = true }

    fun encode(name: String, record: ValuesWithMetaData<IsRootDataModel>, remaining: Int): ByteArray {
        val model = models.getValue(name)
        if (record.key.size != model.Meta.keyByteSize || record.values.dataModel.Meta.name != name ||
            record.values.dataModel.Meta.version.major != model.Meta.version.major
        ) {
            throw RequestException("State backup record does not match its declared model")
        }
        // Values can originate from independently decoded remote definitions. Their enum objects
        // belong to that schema instance; encode through it, then validate against the recipient.
        val context = context(name, record.values.dataModel)
        val cache = WriteCache()
        val size = ValuesWithMetaData.Serializer.calculateObjectProtoBufLength(record, cache, context)
        if (size < 0 || size > remaining) throw RequestException("Serialized state backup exceeds chunk byte limit")
        var index = 0
        return ByteArray(size).also { bytes ->
            ValuesWithMetaData.Serializer.writeObjectProtoBuf(record, cache, { bytes[index++] = it }, context)
            if (record.values.dataModel !== model) decode(name, bytes)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun decode(name: String, bytes: ByteArray): ValuesWithMetaData<IsRootDataModel> {
        var index = 0
        val record = ValuesWithMetaData.Serializer.readProtoBuf(bytes.size, { bytes[index++] }, context(name)).toDataObject() as ValuesWithMetaData<IsRootDataModel>
        if (index != bytes.size) throw RequestException("State backup record contains trailing bytes")
        return record
    }
}
