package maryk.core.models

import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.query.RequestContext
import maryk.core.values.AbstractValues
import maryk.core.values.IsValueItems

/** DataModel definition which can create Values */
interface IsDataModelWithValues<DO: Any, P: AbstractPropertyDefinitions<DO>, V: AbstractValues<DO, *, P>> : IsDataModel<P> {
    /** Create a ObjectValues with given [createMap] function */
    fun map(context: RequestContext? = null, createMap: P.() -> IsValueItems): V
}
