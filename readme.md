# sbt-scalafmt
[![Join the chat at https://gitter.im/scalameta/scalafmt](https://badges.gitter.im/scalameta/scalafmt.svg)](https://gitter.im/scalameta/scalafmt?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Join the discord chat](https://img.shields.io/discord/632642981228314653?label=discord)](https://discordapp.com/channels/632642981228314653/632665341864181780)
[![Latest version](https://index.scala-lang.org/scalameta/sbt-scalafmt/latest.svg?kill_cache=1)](https://index.scala-lang.org/scalameta/sbt-scalafmt)

This is the repository for the Scalafmt sbt plugin, for the main Scalafmt
repository see [scalameta/scalafmt](https://github.com/scalameta/scalafmt/).

### Installation

Add following line into `project/plugins.sbt` (latest version is available next to the
**sbt-scalafmt** badge above)
```sbt
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % PLUGIN_VERSION)
```

If the above does not work, in enterprise environments, try:
```sbt
libraryDependencies += "org.scalameta" % "sbt-scalafmt_2.12_1.0" % PLUGIN_VERSION
```

#### JDK compatibility
| JDK  | Release        |
| ---- | ---------------|
| 8    | Up to `v2.5.1` |
| 11+  | _latest_       |

### [User documentation](https://scalameta.org/scalafmt/)
Head over to [the user docs](https://scalameta.org/scalafmt/docs/installation.html#sbt) for instructions on how to install and use scalafmt.

### Team
The current maintainers (people who can merge pull requests) are:

* Mikhail Chugunkov [`@poslegm`](https://github.com/poslegm)
* Ólafur Páll Geirsson - [`@olafurpg`](https://github.com/olafurpg)
* Rikito Taniguchi [`@tanishiking`](https://github.com/tanishiking)

An up-to-date list of contributors is available here: https://github.com/scalameta/sbt-scalafmt/graphs/contributors

We strive to offer a welcoming environment to learn, teach and contribute.

