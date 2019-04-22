# Collect & Inject

Within Maryk it is possible to inject values into requests. This enables you to 
build requests upon the responses of previous requests. For example you can request
a data object from one data store, collect the results to use reference keys from the 
response to request related data from another data store

Below an example of collecting and getting friends of 2 persons
```yaml
- !Collect
  collectedFriends: !Get
    dataModel: Person
    keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
    filterSoftDeleted: true
- !Get
  dataModel: Person
  keys: !:Inject
    collectedFriends: values.*.values.friends
```
**Maryk Model YAML for Person:**
```yaml
name: Person
properties:
  ? 1: firstName
  : !String
  ? 2: lastName
  : !String
  ? 3: friends
  : !List
    valueDefinition: !Reference
      dataModel: Person
```

## Collect first

To inject data in a later request you have to first collect data from an earlier 
request and store it in a name.

Example within Maryk Yaml requests list. Collects the response of the Get on Persons
into a collection named `friends`.
```yaml
- !Collect
  collectedFriends: !Get
    dataModel: Person
    keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
    filterSoftDeleted: true
```

## Inject the results

To inject a result which you collected you use the !:Inject tag within your request. You then use the
name of the collected results in the key and the reference to the value as the value. You need to include
the path for the entire response.

```yaml
- !Collect
  collectedFriends: !Get
    dataModel: Person
    keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
    filterSoftDeleted: true
- !Get
  dataModel: SimpleMarykModel
  keys: !:Inject
    friends: values.*.values.friends
```
