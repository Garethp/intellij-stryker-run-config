## Changelog

### Version 2021.1
 * Fix some errors that were occurring in the background
 * Introduced re-running failed mutants
 * Moved to new versioning strategy to accommodate Jetbrains Marketplace

### Version 1.0.11
 * Fix an error with reading `package.json` in a non-async context

### Version 1.0.10
 * Fix a regression where inspections weren't appearing

### Version 1.0.9
 * Outputs an HTML report when you run
 * Fixed some instances of smart commands not working
 * Fix the report to show you the mutants before they start running
 * Fix the inspector not changing when the file changes
 * Fix the "Run all files in directory" not detecting all file types

#### Version 1.0.8
 * Improved the accuracy of mutants when the user changes the file before navigating to the mutant results
 * Expanded smart terminal commands to work with the following
   * `yarn stryker run`
   * `yarn [command]` where `[command]` starts with `stryker run` (for example `yarn test:mutation`)

#### Version 1.0.7
 * Improve support for Stryker in workspaces, including different versions in different workspaces

#### Version 1.0.6
 * If you're using Stryker v4, it'll now auto-inject the reporter for you

#### Version 1.0.5
 * Adding a new "Smart Console Intention". Try typing `stryker run` into your console and hitting `Ctrl`+`Enter`

#### Version 1.0.4
 * Automatically sets `.stryker-tmp` folder to be ignored by indexer

#### Version 1.0.3
 * Show surviving mutants as inspection errors
 * Adding ability to jump to mutation by double clicking test name

#### Version 1.0.2
 * Added plugin icon

#### Version 1.0.1
 * Updated instructions in plugin description

#### Version 1.0
 * Adds the ability to run Stryker from within IntelliJ IDE's