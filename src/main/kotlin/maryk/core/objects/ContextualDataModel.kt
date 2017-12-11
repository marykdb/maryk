package maryk.core.objects

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions

/** DataModel for non contextual models
 * @param properties: All definitions for properties contained in this model
 * @param DO: Type of DataObject contained
 * @param P: PropertyDefinitions type for reference retrieval
 */
abstract class ContextualDataModel<DO: Any, out P: PropertyDefinitions<DO>, CXI: IsPropertyContext, CX: IsPropertyContext>(
        properties: P,
        val contextTransformer: (CXI?) -> CX?
) : AbstractDataModel<DO, P, CXI, CX>(properties) {
    override fun transformContext(context: CXI?) = this.contextTransformer(context)
}