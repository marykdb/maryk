package maryk.core.models

import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext

typealias IsSimpleObjectDataModel<DO> = IsObjectDataModel<DO, ObjectPropertyDefinitions<DO>>

/** A DataModel which holds properties and can be validated */
interface IsObjectDataModel<DO: Any, P: ObjectPropertyDefinitions<DO>>: IsDataModelWithValues<DO, P, ObjectValues<DO, P>> {
    /**
     * Validate a [dataObject] and get reference from [refGetter] if exception needs to be thrown
     * @throws ValidationUmbrellaException if input was invalid
     */
    fun validate(dataObject: DO, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>, *>? = { null })

    /** Creates a Data Object by [map] */
    operator fun invoke(map: ObjectValues<DO, P>): DO

    /** Create a ObjectValues with given [createMap] function */
    override fun map(context: RequestContext?, createMap: P.() -> Map<Int, Any?>) =
        ObjectValues(this, createMap(this.properties), context)
}

/**
 * Converts a DataObject back to ObjectValues
 */
fun <DO: Any, DM: IsObjectDataModel<DO, P>, P: ObjectPropertyDefinitions<DO>> DM.asValues(dataObject: DO, context: RequestContext? = null): ObjectValues<DO, P> {
    val mutableMap = mutableMapOf<Int, Any>()

    for (property in this.properties) {
        property.getter(dataObject)?.let {
            mutableMap[property.index] = it
        }
    }

    return this.map(context) {
        mutableMap
    }
}
