package maryk.core.models

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsTypedPropertyDefinitions
import maryk.core.values.AbstractValues
import maryk.core.values.Values

typealias SimpleValuesDataModel<DM> = AbstractDataModel<Any, DM, Values<DM>, IsPropertyContext, IsPropertyContext>

/**
 * A Data Model for converting and validating DataObjects. The [properties] contain all the property definitions for
 * this Model. [DO] is the type of DataObjects described by this model and [CX] the context to be used on the properties
 * to read and write. [CXI] is the input Context for properties. This can be different because the ObjectDataModel can create
 * its own context by transforming the given context.
 */
abstract class AbstractDataModel<DO : Any, DM : IsTypedPropertyDefinitions<DO>, V : AbstractValues<DO, DM>, in CXI : IsPropertyContext, CX : IsPropertyContext> internal constructor(
    final override val properties: DM
) : IsDataModel<DM>
