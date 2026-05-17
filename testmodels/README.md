# Maryk Test Models

Reusable Maryk models for tests across core, stores, CLI, App and serialization modules.

## Purpose

- Exercise all major property definitions.
- Provide stable model IDs and shapes for store behavior tests.
- Avoid every module inventing its own partial test schema.

## Use when

- Writing datastore tests that should match existing behavior.
- Testing serialization or query behavior against realistic models.
- Reproducing bugs that involve nested values, collections, references, indexes or versioned changes.

## Guidelines

- Keep test models stable. Changing an existing model can invalidate store fixtures or compatibility assumptions.
- Add a new model when a behavior needs a distinct shape.
- Prefer small focused models unless the test specifically needs broad property coverage.
