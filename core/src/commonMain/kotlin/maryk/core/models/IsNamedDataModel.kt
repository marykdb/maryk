package maryk.core.models

import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper

interface IsNamedDataModel<P : IsPropertyDefinitions> : IsDataModel<P> {
    val name: String

    /** Get PropertyReference by [referenceName] */
    fun getPropertyReferenceByName(referenceName: String, context: IsPropertyContext? = null) = try {
        this.properties.getPropertyReferenceByName(referenceName, context)
    } catch (e: DefNotFoundException) {
        throw DefNotFoundException("Model ${this.name}: ${e.message}")
    }

    /** Get PropertyReference by bytes by reading the [reader] until [length] is reached. */
    fun getPropertyReferenceByBytes(length: Int, reader: () -> Byte, context: IsPropertyContext? = null) = try {
        this.properties.getPropertyReferenceByBytes(length, reader, context)
    } catch (e: DefNotFoundException) {
        throw DefNotFoundException("Model ${this.name}: ${e.message}")
    }

    /** Get PropertyReference by bytes by reading the storage bytes [reader] until [length] is reached. */
    fun getPropertyReferenceByStorageBytes(length: Int, reader: () -> Byte, context: IsPropertyContext? = null) = try {
        this.properties.getPropertyReferenceByStorageBytes(length, reader, context)
    } catch (e: DefNotFoundException) {
        throw DefNotFoundException("Model ${this.name}: ${e.message}")
    }

    companion object {
        internal fun <DM : IsNamedDataModel<*>> addName(
            definitions: ObjectPropertyDefinitions<DM>,
            getter: (DM) -> String
        ): FlexBytesDefinitionWrapper<String, String, IsPropertyContext, StringDefinition, DM> {
            return definitions.add(1u, "name", StringDefinition(), getter)
        }
    }
}
