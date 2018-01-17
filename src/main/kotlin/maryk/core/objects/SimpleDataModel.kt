package maryk.core.objects

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions

/** DataModel for non contextual models
 * @param properties: All definitions for properties contained in this model
 * @param DO: Type of DataObject contained
 * @param P: PropertyDefinitions type for reference retrieval
 */
abstract class SimpleDataModel<DO: Any, out P: PropertyDefinitions<DO>>(
        properties: P
) : AbstractDataModel<DO, P, IsPropertyContext, IsPropertyContext>(
        properties
)