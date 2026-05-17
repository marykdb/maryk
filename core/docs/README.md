# Core Documentation

Maryk's core module defines the data contract: models, properties, keys, queries, serialization and version-aware behavior.

Recommended order for new users:

1. [Data Design](data-design.md)
2. [Data Models](datamodel.md)
3. [Properties](properties/README.md)
4. [Keys](key.md)
5. [Querying Data](query.md)
6. [Versioning](versioning.md)
7. [Serialization](serialization/README.md)

## Contents

- [Data Design](data-design.md): choose embedding, references, keys, indexes and graphs.
- [Data Models](datamodel.md): define model structures and property indexes.
  - [Properties](properties/README.md): overview of property types, validations, operations and references.
  - [Keys](key.md): design unique and efficient keys for data objects.
  - [Index Design](index-design.md): choose between ordered, normalized, and search-oriented indexes.
- [Versioning](versioning.md): track and request historical value versions.
- [Querying Data](query.md): add, change, get and scan objects.
  - [Reference Graphs](reference-graphs.md): select specific properties to return.
  - [Filters](filters.md): filter operators for narrowing query results.
  - [Aggregations](aggregations.md): aggregate data with value and bucket operators.
- [Collect & Inject](collectAndInject.md): reuse values from previous responses in new requests.
- [Serialization](serialization/README.md): write and read YAML, JSON or ProtoBuf.
  - [ProtoBuf Transport](serialization/protobuf.md): details on ProtoBuf wire formats.

### Properties Subfolder

The [`properties`](properties/README.md) directory dives deeper into property concepts:

- `README.md` – overview of property characteristics and validation
- `operations.md` – property operations such as change or delete
- `references.md` – creating references to properties
- `types/` – documentation of each property type
