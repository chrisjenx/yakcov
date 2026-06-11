# Phone validation

The `Phone` rule validates numbers with
[libphonenumber-kotlin](https://github.com/luca992/libphonenumber-kotlin)
(luca992's multiplatform port of Google's libphonenumber).

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/recipes/Recipes.kt:phone"
```

`Phone` also accepts a `State<String>` for the region, so the default region can react
to a country picker: `Phone(defaultRegion = countryState)`.

## Install (required)

Yakcov declares libphonenumber-kotlin as `compileOnly`, so phone rules need your app
(or shared module) to provide the dependency:

```kotlin
commonMain {
    dependencies {
        implementation("io.github.luca992.libphonenumber-kotlin:libphonenumber:0.1.9")
    }
}
```

Without it, using `Phone` fails at runtime with a missing-class error — everything else
in Yakcov works without the dependency.
