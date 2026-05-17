# Maryk Test Library

Shared test helpers used by Maryk modules.

## Use when

- A module needs common assertions or byte collectors.
- Tests need platform-neutral helpers.
- Store and serialization tests need reusable utilities.

## Notes

- Intended for Maryk's own test suites.
- Keep helpers small and dependency-light.
- Prefer common code so JVM, JS and native tests can reuse it.
