package maryk.core.properties.definitions.index

import maryk.core.properties.references.IsIndexablePropertyReference

@Suppress("UNCHECKED_CAST")
fun IsIndexablePropertyReference<*>.normalize() = Normalize(this as IsIndexablePropertyReference<String>)

@Suppress("UNCHECKED_CAST")
fun IsIndexablePropertyReference<*>.split(on: SplitOn) = Split(this as IsIndexablePropertyReference<String>, on)

fun AnyOf.normalize() = AnyOf(name, references.map { Normalize(it) })

fun AnyOf.split(on: SplitOn) = AnyOf(name, references.map { Split(it, on) })
