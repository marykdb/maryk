package maryk.core.models

import maryk.core.objects.AbstractValues
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.query.DefinitionsContext

/** DataModel definition which can create Values */
interface IsDataModelWithValues<DO: Any, P: AbstractPropertyDefinitions<DO>, V: AbstractValues<DO, *, P>> : IsDataModel<P> {
    /** Create a ObjectValues with given [createMap] function */
    fun map(context: DefinitionsContext? = null, createMap: P.() -> Map<Int, Any?>): V
}
