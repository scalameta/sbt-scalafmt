This template is mostly a guideline, not a strict requirement, except
the sections marked `(required)`.

Please make sure that the issue is specific to the plugin for sbt that is
observable only using the latest version of `sbt-scalafmt` and does not
appear if you use the `scalafmt` **CLI** using the same `.scalafmt.conf`:
`https://scalameta.org/scalafmt/docs/installation.html#cli`

Prior check via the CLI is _mandatory_.

## Configuration (required) ##

Please paste the values of all `sbt-scalafmt`
[settings](https://scalameta.org/scalafmt/docs/installation.html#settings)
found in your `build.sbt` file here:
```
scalafmtConfig = <please enter the file location here>
...
```

Please paste the contents of your `.scalafmt.conf` file (mentioned above
under `scalafmtConfig` sbt setting) here:
```
version = <please enter the version here and make sure it's the latest>
...
```

NB: before submitting, please confirm that the problem is observed in
the *latest published* version of the sbt plugin! We don't publish
hotfixes for older versions, and the problem you have observed in an
older version may have already been fixed.

## Command-line parameters (required) ##

When I run sbt-scalafmt via `sbt` like this: `<sbt command-line parameter>`

## Problem (required)

`sbt-scalafmt` fails to format files mentioned below, or issues an
error as follows:
```scala
OUTPUT FROM SBT-SCALAFMT
```

## Expectation

...

## Workaround

I've found that by...

## Notes

See also...
