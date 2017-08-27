# ChangeLog for the Tensei-Data Server

All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

## Conventions when editing this file.

Please follow the listed conventions when editing this file:

* one subsection per version
* reverse chronological order (latest entry on top)
* write all dates in iso notation (`YYYY-MM-DD`)
* each version should group changes according to their impact:
    * `Added` for new features.
    * `Changed` for changes in existing functionality.
    * `Deprecated` for once-stable features removed in upcoming releases.
    * `Removed` for deprecated features removed in this release.
    * `Fixed` for any bug fixes.
    * `Security` to invite users to upgrade in case of vulnerabilities.

## Unreleased

## 1.13.3 (2017-06-02)

- no significant changes

## 1.13.2 (2017-05-24)

### Changed

- watchdog will remove unreachable frontend nodes from the cluster

## 1.13.1 (2017-05-18)

- no significant changes

## 1.13.0 (2017-05-03)

### Added

- include free license in distribution
- use free license if commercial license is not installed or invalid

### Changed

- restructure sbt configuration
- switch to scalafmt for code formatting
- update Scala to 2.11.11
- disable interactive console by default

### Removed

- usage of deprecated `BaseApplication` and `Settings` from tensei api

## 1.12.1 (2017-03-22)

- no changes

## 1.12.0 (2017-03-13)

### Added

- resolver settings for local ivy repository

### Changed

- update Akka to 2.4.17
- update dfasdl to 1.25.0
- update logback to 1.2.1
- update tensei-api to 1.87.0
- update sbt-wartremover to 2.0.2
- update sbt-pgp to 1.0.1
- update sbt-native-packager to 1.2.0-M8
- stricter compiler settings
- Code Cleanup

## 1.11.0 (2017-01-20)

- no significant changes

## 1.10.0 (2017-01-04)

- no significant changes

## 1.9.2 (2016-11-30)

### Changed

- specify scala version using `in ThisBuild` in sbt
- allow parallel test execution (removed settings that forced sequential execution)
- update sbt-native-packager to 1.2.0-M7

## 1.9.1 (2016-11-22)

### Removed

- publish completed transformation events to the pub sub mediator (fixes multiple trigger execution bug)

## 1.9.0 (2016-11-10)

### Added

- `application.ini` for runtime configuration options
- execute tests before building debian package
- defaults for logback configuration options
- activator binary 1.3.12

### Changed

- update Akka to 2.4.12
- adjust code according to new Akka release
- update SBT to 0.13.13
- update sbt-native-packager to 1.2.0-M5
- update sbt-wartremover to 1.1.1
- update ScalaTest to 3.0.0
- code cleanup
- adjusted [README.md](README.md)

### Removed

- xsbt-filter plugin
- custom templates for sbt-native-packager

## 1.8.0 (2016-06-22)

### Added

- collaboration files
    - [AUTHORS.md](AUTHORS.md)
    - this CHANGELOG file
    - [CONTRIBUTING.md](CONTRIBUTING.md)
    - [LICENSE](LICENSE)
- relaying of messages
- new compiler flags
- create .deb package via sbt-native-packager

### Changed

- disable logging to database
- remove log message formatting via `s"$foo"`
- switch versioning to sbt-git

### Fixed

- agent becoming unauthorised after ping
- license check incorrect for long running systems

## 1.7.0 (2016-03-03)

- no significant changes

## 1.6.0 (2016-01-21)

### Changed

- adjust server for usage of `ElementReference`

## 1.5.0 (2015-11-30)

### Changed

- update Akka to 2.3.14

## 1.4.2 (2015-10-13)

- no significant changes

## 1.4.1 (2015-10-12)

- no significant changes

## 1.4.0 (2015-09-29)

- no significant changes

## 1.3.0 (2015-08-27)

### Added

- more tests

### Changed

- using disjunction of Scalaz

### Fixed

- agent not recognised by server
-

## 1.2.0 (2015-08-03)

### Changed

- update Akka to 2.3.12
- update Scala to 2.11.7

## 1.1.1 (2015-07-14)

- no significant changes

## 1.1.0 (2015-06-29)

### Fixed

- server does not start

## 1.0.0 (2015-06-01)

Initial release.
