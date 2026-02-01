package io.maryk.app

import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.aggregations.bucket.DateHistogram
import maryk.core.aggregations.bucket.EnumValues
import maryk.core.aggregations.bucket.Types
import maryk.core.aggregations.metric.Average
import maryk.core.aggregations.metric.Max
import maryk.core.aggregations.metric.Min
import maryk.core.aggregations.metric.Stats
import maryk.core.aggregations.metric.Sum
import maryk.core.aggregations.metric.ValueCount
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsTypedDataModel
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsNumericDefinition
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.DateUnit
import maryk.core.query.RequestContext

enum class AggregationMetric(val label: String) {
    VALUE_COUNT("Count"),
    SUM("Sum"),
    AVERAGE("Average"),
    MIN("Min"),
    MAX("Max"),
    STATS("Stats");

    fun supports(definition: IsPropertyDefinition<*>?): Boolean {
        return when (this) {
            VALUE_COUNT -> true
            SUM, AVERAGE, STATS -> definition is IsNumericDefinition<*>
            MIN, MAX -> definition is IsComparableDefinition<*, *>
        }
    }
}

enum class AggregationBucket(val label: String) {
    NONE("None"),
    DATE_HISTOGRAM("Date histogram"),
    ENUM_VALUES("Enum values"),
    TYPES("Types"),
    ;

    fun supports(definition: IsPropertyDefinition<*>?): Boolean {
        return when (this) {
            NONE -> true
            DATE_HISTOGRAM -> definition is DateDefinition || definition is DateTimeDefinition || definition is TimeDefinition
            ENUM_VALUES -> definition is EnumDefinition<*>
            TYPES -> definition is IsMultiTypeDefinition<*, *, *>
        }
    }
}

data class AggregationDefinition(
    val id: String,
    val label: String,
    val bucket: AggregationBucket,
    val bucketField: String,
    val metric: AggregationMetric,
    val metricField: String,
    val dateUnit: DateUnit = DateUnit.Days,
)

data class AggregationConfig(
    val filterText: String = "",
    val limit: Int = 500,
    val definitions: List<AggregationDefinition> = emptyList(),
)

internal data class AggregationField(
    val path: String,
    val definition: IsPropertyDefinition<*>,
)

internal fun collectAggregationFields(model: IsTypedDataModel<*>): List<AggregationField> {
    val fields = mutableListOf<AggregationField>()
    model.forEach { wrapper ->
        collectDefinitionFields(wrapper.definition, wrapper.name, fields)
    }
    return fields
}

private fun collectDefinitionFields(
    definition: IsPropertyDefinition<*>,
    path: String,
    output: MutableList<AggregationField>,
) {
    when (definition) {
        is IsEmbeddedDefinition<*> -> {
            definition.dataModel.forEach { wrapper ->
                collectDefinitionFields(wrapper.definition, "$path.${wrapper.name}", output)
            }
        }
        is IsMultiTypeDefinition<*, *, *> -> {
            output.add(AggregationField(path, definition))
            definition.typeEnum.cases().forEach { typeCase ->
                val sub = definition.definition(typeCase.index) ?: return@forEach
                collectDefinitionFields(sub, "$path.*${typeCase.name}", output)
            }
        }
        is IsMapDefinition<*, *, *> -> {
            collectDefinitionFields(definition.valueDefinition, "$path.value", output)
        }
        is IsListDefinition<*, *> -> {
            collectDefinitionFields(definition.valueDefinition, "$path.value", output)
        }
        is IsSetDefinition<*, *> -> {
            collectDefinitionFields(definition.valueDefinition, "$path.value", output)
        }
        else -> output.add(AggregationField(path, definition))
    }
}

internal fun buildAggregations(
    dataModel: IsRootDataModel,
    requestContext: RequestContext,
    definitions: List<AggregationDefinition>,
): Aggregations {
    if (definitions.isEmpty()) {
        throw IllegalArgumentException("No aggregations configured.")
    }
    val fieldDefinitions = collectAggregationFields(dataModel).associateBy { it.path }
    val result = linkedMapOf<String, IsAggregationRequest<*, *, *>>()
    for (definition in definitions) {
        val label = uniqueAggregationLabel(definition.label, definition, result.keys)
        val request = buildAggregationRequest(definition, fieldDefinitions, dataModel, requestContext)
        result[label] = request
    }
    return Aggregations(*result.map { it.key to it.value }.toTypedArray())
}

private fun uniqueAggregationLabel(
    rawLabel: String,
    definition: AggregationDefinition,
    existing: Set<String>,
): String {
    val base = rawLabel.trim().ifBlank {
        if (definition.bucket == AggregationBucket.NONE) {
            "${definition.metric.label} ${definition.metricField}".trim()
        } else {
            "${definition.bucket.label} ${definition.bucketField}".trim()
        }
    }.ifBlank { "Aggregation" }
    if (base !in existing) return base
    var index = 2
    var candidate = "$base $index"
    while (candidate in existing) {
        index += 1
        candidate = "$base $index"
    }
    return candidate
}

private fun buildAggregationRequest(
    definition: AggregationDefinition,
    fieldDefinitions: Map<String, AggregationField>,
    dataModel: IsRootDataModel,
    requestContext: RequestContext,
): IsAggregationRequest<*, *, *> {
    val metricField = definition.metricField.ifBlank { definition.bucketField }.trim()
    if (metricField.isBlank()) {
        throw IllegalArgumentException("Metric field is missing.")
    }
    val metricDefinition = fieldDefinitions[metricField]?.definition
    if (metricDefinition != null && !definition.metric.supports(metricDefinition)) {
        throw IllegalArgumentException("${definition.metric.label} needs a numeric or comparable field.")
    }
    val metricReference = resolveReference(metricField, dataModel, requestContext)
    val metricAggregation = buildMetricAggregation(definition.metric, metricReference)

    if (definition.bucket == AggregationBucket.NONE) {
        return metricAggregation
    }

    val bucketField = definition.bucketField.trim()
    if (bucketField.isBlank()) {
        throw IllegalArgumentException("Bucket field is missing.")
    }
    val bucketDefinition = fieldDefinitions[bucketField]?.definition
    if (bucketDefinition != null && !definition.bucket.supports(bucketDefinition)) {
        throw IllegalArgumentException("${definition.bucket.label} needs a matching field type.")
    }
    val bucketReference = resolveReference(bucketField, dataModel, requestContext)
    val nested = Aggregations("metric" to metricAggregation)

    @Suppress("UNCHECKED_CAST")
    return when (definition.bucket) {
        AggregationBucket.DATE_HISTOGRAM -> DateHistogram(
            reference = bucketReference as IsPropertyReference<Comparable<Any>, IsPropertyDefinition<Comparable<Any>>, *>,
            dateUnit = definition.dateUnit,
            aggregations = nested,
        )
        AggregationBucket.ENUM_VALUES -> EnumValues(
            reference = bucketReference as IsPropertyReference<IndexedEnumComparable<Any>, IsPropertyDefinition<IndexedEnumComparable<Any>>, *>,
            aggregations = nested,
        )
        AggregationBucket.TYPES -> {
            val multiTypeDefinition = bucketDefinition as? IsMultiTypeDefinition<TypeEnum<Any>, Any, *>
                ?: throw IllegalArgumentException("Types bucket requires multi-type field.")
            val typeRef = multiTypeDefinition.typeRef(bucketReference)
            Types(typeRef, aggregations = nested)
        }
        AggregationBucket.NONE -> metricAggregation
    }
}

@Suppress("UNCHECKED_CAST")
private fun buildMetricAggregation(
    metric: AggregationMetric,
    reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>,
): IsAggregationRequest<*, *, *> {
    val cast = reference as IsPropertyReference<Comparable<Any>, IsPropertyDefinition<Comparable<Any>>, *>
    return when (metric) {
        AggregationMetric.VALUE_COUNT -> ValueCount(cast)
        AggregationMetric.SUM -> Sum(cast)
        AggregationMetric.AVERAGE -> Average(cast)
        AggregationMetric.MIN -> Min(cast)
        AggregationMetric.MAX -> Max(cast)
        AggregationMetric.STATS -> Stats(cast)
    }
}

private fun resolveReference(
    path: String,
    dataModel: IsRootDataModel,
    requestContext: RequestContext,
): IsPropertyReference<*, IsPropertyDefinition<*>, *> {
    return dataModel.getPropertyReferenceByName(path, requestContext)
}