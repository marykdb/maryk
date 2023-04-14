package maryk.core.models

/**
 * Base class for all Object based DataModels.
 * Implements IsObjectDataModel and provides methods to get properties by name or index.
 */
abstract class BaseObjectDataModel<DO : Any> : BaseDataModel<DO>(), IsObjectDataModel<DO> {
    /** Get a method to retrieve property from DataObject by [name] */
    fun getPropertyGetter(name: String): ((DO) -> Any?)? = nameToDefinition[name]?.run { { getPropertyAndSerialize(it, null) } }

    /** Get a method to retrieve property from DataObject by [index] */
    fun getPropertyGetter(index: UInt): ((DO) -> Any?)? = indexToDefinition[index]?.run { { getPropertyAndSerialize(it, null) } }
}
