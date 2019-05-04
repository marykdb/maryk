package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition

/** Defines that this reference part can contain a list item reference. */
interface CanContainListItemReference<T: Any, out D: IsPropertyDefinition<T>, V: Any>: IsPropertyReference<T, D, V>
