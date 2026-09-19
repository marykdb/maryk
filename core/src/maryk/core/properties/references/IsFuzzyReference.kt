package maryk.core.properties.references

import maryk.core.processors.datastore.matchers.IsFuzzyMatcher

/** Defines a non-exact match reference. Mostly for any list and maps */
interface IsFuzzyReference {
    /** get a fuzzy matcher for this reference */
    fun fuzzyMatcher(): IsFuzzyMatcher
}
