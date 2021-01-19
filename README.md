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
#### Reporter Setup (Only if Stryker is < 4.0)
When your version of Stryker is < 4.0, this plugin needs a reporter added to Stryker, *stryker-intellij-reporter*.
If you have version 4.0 or newer, this plugin provides the reporter automatically, so there's no need to do anything.

So if you have a version older than 4.0, first you need to install it as a dependency:
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