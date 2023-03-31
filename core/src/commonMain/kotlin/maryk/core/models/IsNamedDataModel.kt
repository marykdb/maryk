package maryk.core.models

import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsPropertyDefinitions

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
}
