# Contributing

Thanks for your interest. PaperCript has a single maintainer, so replies can take a while.

## Bugs and ideas

Open an [issue](https://github.com/javimentallab/PaperCript/issues). Say what you expected, what happened, and which version and system you use. English or Spanish are both fine.

Security problems go through [SECURITY.md](SECURITY.md), not through issues.

## Changing the code

For anything big, open an issue first so we can agree on the approach.

```sh
./gradlew testDebugUnitTest assembleDebug
```

It needs JDK 21.

- **Tests:** anything important that you add comes with a test.
- **Dependencies:** a new one needs a permissive licence (MIT, BSD, Apache…). Check it before adding it. After changing dependencies, refresh `app/gradle.lockfile`, the list that Trivy reads: `./gradlew -I gradle/dependency-lock.init.gradle :app:dependencies --write-locks`.
- **Private data:** no personal paths, hostnames, IP addresses or tokens in the code, the history or the screenshots.
- **Interface text:** new text goes in every language, and has to fit on screen in all of them. Texts are in `app/src/main/res/values*/strings.xml`, and the store texts in `fastlane/metadata/android/`.
- **Licence:** your contribution is released under the project's [MIT licence](LICENSE).
