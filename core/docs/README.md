# Core Documentation

Maryk's core module provides the building blocks for defining data models and interacting with them. The guides in this folder walk you through those features step by step. Start with **Data Models** to describe your domain, explore **Properties** to learn which types are available, and then move on to querying and serialization. Each page is linked below for easy navigation.

## Contents

- [Data Models](datamodel.md): define model structures and property indexes.
  - [Properties](properties/README.md): overview of property types, validations, operations and references.
  - [Keys](key.md): design unique and efficient keys for data objects.
- [Versioning](versioning.md): track and request historical value versions.
- [Querying Data](query.md): add, change, get and scan objects.
  - [Reference Graphs](reference-graphs.md): select specific properties to return.
  - [Filters](filters.md): filter operators for narrowing query results.
  - [Aggregations](aggregations.md): aggregate data with value and bucket operators.
- [Collect & Inject](collectAndInject.md): reuse values from previous responses in new requests.
- [Serialization](serialization.md): write and read YAML, JSON or ProtoBuf.
  - [ProtoBuf Transport](protobuf.md): details on ProtoBuf wire formats.

### Properties Subfolder

The [`properties`](properties/README.md) directory dives deeper into property concepts:

- `README.md` – overview of property characteristics and validation
- `operations.md` – property operations such as change or delete
- `references.md` – creating references to properties
- `types/` – documentation of each property type
