package maryk.core.models

import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.migration.MigrationStatus
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

    override fun isMigrationNeeded(
        storedDataModel: IsDataModel<*>,
        migrationReasons: MutableList<String>
    ): MigrationStatus {
        if (storedDataModel is IsNamedDataModel<*>) {
            if (storedDataModel.name != this.name) {
                migrationReasons += "Names of models did not match: ${storedDataModel.name} -> ${this.name}"
            }
        } else {
            migrationReasons += "Stored model is not a named data model"
        }

        return super.isMigrationNeeded(storedDataModel, migrationReasons)
    }
}
