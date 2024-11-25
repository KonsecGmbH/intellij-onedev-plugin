# Dev notes

This plugin was built using https://github.com/JetBrains/intellij-platform-plugin-template,
so familiarize yourself with the [README.md](README.md) first

## Build locally
`./gradlew buildPlugin`

## Test
Install the plugin using the `intellij-onedev-plugin-0.0.1.zip` located under `build/distributions` folder

## Server Configuration
- Server URL
- Access Token
- TODO: username/password?

## Integration testing
In addition to default testing routines coming with the plugin template, there an end-to-end integration test
running against the OneDev Docker image integrated into thr `build.yml` pipeline. How to run locally:
- Run `onedev.sh` to launch the Docker image
- Run `OneDevRepositoryTest.java`

## Q:
- Do we need automated upload?
