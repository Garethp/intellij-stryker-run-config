## IntelliJ Stryker Plugin
Integrates Stryker-JS with the intellij test runner to give you native test runner for Mutation Testing
### Build
```bash
./gradlew buildPlugin
````
### Run
```bash
./gradlew runIdea
```                                             
#### Reporter Setup
This plugin needs a reporter added to Stryker, *stryker-intellij-reporter*. So first you need to install it as a dependency:
```bash
npm i stryker-intellij-reporter -D
````

And then it needs to be added as a plugin to the Stryker Config:

```json
{
    "plugins": [
        "@stryker-mutator/*",
        "stryker-intellij-reporter"
    ]
}
````

#### Mutating Files
Right click a file, or it's corresponding test file, then select "Mutate [filename]"

##### Runner limitations:
1. At the moment you can't re-run failed mutations
2. You can't run a specific mutant yet
3. You can't debug mutations