package maryk.core.query.changes

import maryk.core.query.DefinedByReference

/** An operation on a property of type [T] */
interface IsPropertyOperation<T: Any> : IsChange, DefinedByReference<T>
