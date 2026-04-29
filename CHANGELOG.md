<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-onedev-plugin Changelog

## [Unreleased]

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
