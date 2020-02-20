package maryk.core.processors.datastore.matchers

import maryk.core.properties.references.ObjectReferencePropertyReference

/**
 * Describes a [qualifierMatcher] to match qualifier behind a [reference]
 */
class ReferencedQualifierMatcher(
    val reference: ObjectReferencePropertyReference<*, *, *, *>,
    val qualifierMatcher: IsQualifierMatcher
)
