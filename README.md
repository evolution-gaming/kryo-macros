# Kryo Macros [![Build Status](https://travis-ci.org/evolution-gaming/kryo-macros.svg)](https://travis-ci.org/evolution-gaming/kryo-macros) [![license](http://img.shields.io/:license-Apache%202-green.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) [ ![version](https://api.bintray.com/packages/evolutiongaming/maven/kryo-macros/images/download.svg) ](https://bintray.com/evolutiongaming/maven/kryo-macros/_latestVersion)

Scala macros that generate `com.esotericsoftware.kryo.Serializer` implementations in compile time, based on compile time reflection.

## Features and limitations

- On top level only case classes are supported
- Fields of case classes can be other case classes, Scala collections, options, primitive or `AnyVal` types & classes, 
  tuples, Scala enums, standard types & classes: `String`, `Either`, `BigDecimal`, `java.time.Instant`, 
  `scala.concurrent.duration.FiniteDuration`, `org.joda.time.DateTime`
- Fields can be annotated as transient or just be not defined in constructor to avoid parsing and serializing 
- For nested structures need to generate serializers for all case classes 
- Implicitly defined mapping helpers are supported for ADT structures, simple alternative mappings, etc.
- Manual serializers can be used in generated code when defined as implicits

## How to use

Add the following resolver
```sbt
resolvers += Resolver.bintrayRepo("evolutiongaming", "maven")
```

Add the library to your dependencies list
```sbt
libraryDependencies += "com.evolutiongaming" %% "kryo-macros" % "1.3.0"
```

Generate some serializers for your case classes
```scala
import com.evolutiongaming.kryo.Serializer

case class Player(name: String)

val serializer = Serializer.make[Player]
```
 
That's it! You have generated a `com.esotericsoftware.kryo.Serializer` implementation for your `Player`.
You must know what to do with it if you are here :)

To serialize objects that extends sealed traits/class use `Serializer.makeCommon` call: 
```scala
import com.evolutiongaming.kryo.{ConstSerializer, Serializer}
 
sealed trait Reason
 
object Reason {
  case object Close extends Reason
  case object Pause extends Reason       
}

val reasonSerializer = Serializer.makeCommon[Reason] {
  case 0 => ConstSerializer(Reason.Close)
  case 1 => ConstSerializer(Reason.Pause)
}

sealed abstract class Message(val text: String)

object Message {
  case object Common extends Message("common")
  case object Notification extends Message("notification")
}

private implicit val messageSerializer = Serializer.makeMapping[Message] {
  case 0 => Message.Common   
  case 1 => Message.Notification
}
```

To see generated code just add the following line to your sbt build file 
```sbt
scalacOptions += "-Xmacro-settings:print-serializers"
```

For more examples, please, check out 
[SerializerMacroSpec](https://github.com/evolution-gaming/kryo-macros/tree/master/macros/src/test/scala/com/evolutiongaming/kryo/SerializerMacroSpec.scala)

## How to develop

### Run tests, check coverage & binary compatibility for both supported Scala versions
```sh
sbt clean +coverage +test +coverageReport +mimaReportBinaryIssues
```

### Run benchmarks
```sh
sbt -no-colors clean 'benchmark/jmh:run -prof gc .*SerializerBenchmark.*' >results.txt
```

### Release

For version numbering use [Recommended Versioning Scheme](http://docs.scala-lang.org/overviews/core/binary-compatibility-for-library-authors.html#recommended-versioning-scheme)
that is widely adopted in the Scala ecosystem.

Double-check binary & source compatibility and release using following command (credentials required):

```sh
sbt release
```
