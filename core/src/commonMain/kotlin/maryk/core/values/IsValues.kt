package maryk.core.values

import maryk.core.properties.IsPropertyDefinitions

/** A Values object with multiple ValueItems */
interface IsValues<P: IsPropertyDefinitions>: Iterable<ValueItem>, IsValuesGetter
