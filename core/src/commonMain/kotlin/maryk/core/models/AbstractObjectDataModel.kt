package maryk.core.models

import maryk.core.properties.IsObjectPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.values.ObjectValues

typealias SimpleObjectDataModel<DO, P> = AbstractObjectDataModel<DO, P, IsPropertyContext, IsPropertyContext>

/**
 * A Data Model for converting and validating DataObjects. The [properties] contain all the property definitions for
 * this Model. [DO] is the type of DataObjects described by this model and [CX] the context to be used on the properties
 * to read and write. [CXI] is the input Context for properties. This can be different because the ObjectDataModel can create
 * its own context by transforming the given context.
 */
abstract class AbstractObjectDataModel<DO : Any, P : IsObjectPropertyDefinitions<DO>, in CXI : IsPropertyContext, CX : IsPropertyContext> internal constructor(
    properties: P
) : IsDataModel<P>, AbstractDataModel<DO, P, ObjectValues<DO, P>, CXI, CX>(properties)
