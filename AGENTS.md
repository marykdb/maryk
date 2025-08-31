Welcome agent!

Read the README.md file for a general introduction.

The project contains multiple modules with each a README and a docs/documentation folder. 

When implementing fixes, you don't need to run the full test suite but only the one related to the 
module you are working on. If you only did changes in common code it is sufficient to only run the JVM tests
through gradle. You don't need to do a full build as the tests already builds the relevant code.

When writing code:
- Always use imports and not fully qualified names
