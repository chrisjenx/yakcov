# Releasing

- Switch to/Create a release branch matching the `compose` version in `libs.versions.toml`
- Create a tag matching the version in release branch version, increment by 1 for each tag
- Run the following command to build and release to maven central:

```bash
./gradlew :library:publishAndReleaseToMavenCentral --no-configuration-cache -Drelease=true
```

You will need the Yakcov keys on your machine to release to maven central.
This will be changed to github actions in the future.
