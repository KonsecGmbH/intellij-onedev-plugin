<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-onedev-plugin Changelog

## [Unreleased]

## [1.0.2]
### Added
- Shared server profiles: OneDev connection settings can be saved as named, application-level profiles and reused when configuring the repository in any project (API token and mTLS certificate password are kept in the IDE `PasswordSafe`, never in the serialized state)

### Changed
- Update IntelliJ Platform Gradle plugin from 2.18.0 to 2.18.1
- Update Kotlin from 2.4.0 to 2.4.20
- Update Kover from 0.9.8 to 0.9.9
- Update Qodana Gradle plugin from 2026.1.3 to 2026.2.1
- Update Gradle wrapper from 9.6.1 to 9.7.1
- Update CI actions (gradle/actions 4→6.3.0, actions/setup-java 5→6)

### Fixed
- Qodana CI: align the linter with the 2026.1 action and Java 21

## [1.0.1]
### Changed
- Update IntelliJ Platform Gradle plugin from 2.15.0 to 2.18.0
- Update Kotlin from 2.3.21 to 2.4.0
- Update Gradle wrapper from 9.5.0 to 9.6.1
- Update Qodana Gradle plugin from 2026.1.0 to 2026.1.3
- Update CI actions (codecov-action 6→7, actions/checkout 6→7, actions/cache 5→6)

### Fixed
- Flaky `verifyPlugin` CI failure caused by the Android Studio releases feed

## [1.0.0]
### Added
- OneDev Builds integration: tool window showing CI build status, log streaming, and tests
- Show full project path (e.g. `main/mesgitserver`) in Builds table and project filter
- Log button column in Builds view for one-click log access
- Loading spinner in Build Log panel while waiting for first log data
- Dark-theme icon variants for better visibility on dark IDE themes

### Fixed
- Build log parsing: handle ISO 8601 date strings from OneDev API
- Build log style parsing: handle OneDev's style-as-object format

## [0.0.9]
### Changed
- Update IntelliJ Platform target from 2024.2 to 2025.1.7
- Modernize build configuration; downgrade Java toolchain to 21

## [0.0.8]
### Fixed
- Eliminate duplicated `Authorization` header sent to OneDev server

## [0.0.7]
### Changed
- Task states are now loaded dynamically from the server instead of being hard-coded

## [0.0.6]
### Added
- Task types are now loaded dynamically from the server instead of being hard-coded
- P12 certificate field uses a file chooser dialog

### Fixed
- Server settings were not saved after closing the settings dialog

## [0.0.5]
### Added
- mTLS (mutual TLS) client certificate support for connecting to OneDev servers that require it

## [0.0.4]
### Fixed
- UI fixes for the OneDev repository editor

## [0.0.3]
### Added
- Log in using username and password
- Search query support for task lookup

### Fixed
- Handle OneDev `id` vs `number` distinction for issues; load projects correctly

## [0.0.1]
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- OneDev task provider: browse and update issues from IntelliJ IDEA's Tasks & Contexts
