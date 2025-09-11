# Protobuf Transportation

The encoding standard for [ProtoBuf V3](https://developers.google.com/protocol-buffers/) has been adopted for efficient
and compact transportation of data. Developed by Google, ProtoBuf is a widely adopted standard used across various
platforms and languages for serializing structured data. Currently, only the encoding standard has been implemented,
while schema generation is yet to be developed.

For a more in-depth understanding of how values are encoded, refer to
the [ProtoBuf encoding documentation](https://developers.google.com/protocol-buffers/docs/encoding), which provides
detailed insights on the encoding mechanisms and examples.

## Key Value Pairs

A ProtoBuf message is constructed using key-value pairs. In this structure, the key consists of a tag that uniquely
identifies the encoded property, along with a wire type that specifies the type of value being encoded. The actual value
is then represented in byte format for efficient transport. The encoding format for each property type is thoroughly
documented in the [properties documentation](properties/properties.md), which serves as a reference for developers
looking to implement and understand the specifics of encoding different data types.

## Wire Types

Maryk supports all wire types defined by ProtoBuf, including:

* **VarInt**: A variable-length integer used for numeric values, allowing for efficient storage of small values while
  accommodating larger integers without wasting space.
* **Length Delimited**: This type is utilized for values that can vary in length. The actual bytes of the value are
  prefixed by a length field, making it versatile for use with strings and byte arrays. Additionally, it can encapsulate
  key-value pairs of embedded messages.
* **32 Bit**: Specifically employed for values that are exactly 4 bytes in size, commonly used for fixed-width numerical
  types.
* **64 Bit**: Designed for values that are exactly 8 bytes, typically used for large numerical types or higher-precision
  floating-point numbers.
* **Start Group / End Group**: These wire types are currently not in use and are also deprecated in the ProtoBuf
  specification. It is advisable to avoid using them in new implementations.
