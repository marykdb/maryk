package maryk.core.properties.references

import maryk.core.properties.definitions.IsFixedBytesEncodable

interface IsFixedBytesPropertyReference<T : Any> : IsFixedBytesEncodable<T>, IsIndexablePropertyReference<T>
