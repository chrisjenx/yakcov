# Releasing

- Switch to release branch/tag
- Manual release is currently run with:

```bash
./gradlew :library:publishAndReleaseToMavenCentral --no-configuration-cache -Drelease=true
```

You will need the yakcov keys on your machine to release to maven central.
This will be changed to github actions in the future.
