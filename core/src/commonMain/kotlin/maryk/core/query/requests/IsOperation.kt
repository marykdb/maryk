package maryk.core.query.requests

import maryk.core.definitions.Operation

/** Defines an operation which can be done on the stores */
interface IsOperation {
    val operationType: Operation
}
