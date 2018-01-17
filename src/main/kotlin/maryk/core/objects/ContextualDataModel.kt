package maryk.core.objects

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions

/** DataModel which create contexts so properties know the values of other properties.
 * Use it to create a context on starting an action which needs a context.
 * This context is cached if it is needed to read multiple times
 *
 * @param properties: All definitions for properties contained in this model
 * @param DO: Type of DataObject contained
 */
abstract class ContextualDataModel<DO: Any, out P: PropertyDefinitions<DO>, in CXI: IsPropertyContext, CX: IsPropertyContext>(
        properties: P,
        val contextTransformer: (CXI?) -> CX?
) : AbstractDataModel<DO, P, CXI, CX>(properties) {
    override fun transformContext(context: CXI?) = this.contextTransformer(context)
}