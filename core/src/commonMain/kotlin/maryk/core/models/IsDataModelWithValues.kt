package maryk.core.models

import maryk.core.properties.IsObjectPropertyDefinitions
import maryk.core.query.RequestContext
import maryk.core.values.AbstractValues
import maryk.core.values.IsValueItems

/** DataModel definition which can create Values */
interface IsDataModelWithValues<DO : Any, DM : IsObjectPropertyDefinitions<DO>, V : AbstractValues<DO, DM>> :
    IsDataModel<DM> {
    /** Create a ObjectValues with given [createValues] function */
    fun values(context: RequestContext? = null, createValues: DM.() -> IsValueItems): V
}
