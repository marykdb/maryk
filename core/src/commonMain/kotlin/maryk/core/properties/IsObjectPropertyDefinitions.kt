package maryk.core.properties

import maryk.core.query.RequestContext
import maryk.core.values.IsValueItems
import maryk.core.values.ObjectValues

interface IsTypedObjectPropertyDefinitions<DO: Any, P: IsObjectPropertyDefinitions<DO>>: IsObjectPropertyDefinitions<DO> {
    operator fun invoke(values: ObjectValues<DO, P>): DO
}

interface IsObjectPropertyDefinitions<DO: Any>: IsTypedPropertyDefinitions<DO>

/** Create a Values object with given [createMap] function */
fun <DO: Any, DM : IsObjectPropertyDefinitions<DO>> DM.values(
    context: RequestContext? = null,
    createMap: DM.() -> IsValueItems
) =
    ObjectValues(this, createMap(this), context)
