# Kryo Macros [![Build Status](https://travis-ci.org/evolution-gaming/kryo-macros.svg)](https://travis-ci.org/evolution-gaming/kryo-macros) [![Coverage Status](https://coveralls.io/repos/evolution-gaming/kryo-macros/badge.svg)](https://coveralls.io/r/evolution-gaming/kryo-macros) [ ![version](https://api.bintray.com/packages/evolutiongaming/maven/kryo-macros/images/download.svg) ](https://bintray.com/evolutiongaming/maven/kryo-macros/_latestVersion)

Macros that generate com.esotericsoftware.kryo.Serializer implementations in compile time, based on compile time reflection.

How to use
===========

Add the following resolver
```sbt
resolvers += Resolver.bintrayRepo("evolutiongaming", "maven")
```
    
Add the library to your dependencies list
```sbt
libraryDependencies += "com.evolutiongaming" %% "kryo-macros" % "1.1.4"
```
    
Generate some serializers for your case classes
```scala
import com.evolutiongaming.kryo.Serializer

case class Player(name: String)

val serializer = Serializer.make[Player]
 ```
    
That's it! You have generated a com.esotericsoftware.kryo.Serializer implementation for your Player.
You must know what to do with it if you are here :)

How to see generated code
=========================

Just add the following line to your sbt build file 
```scala
scalacOptions += "-Xmacro-settings:print-serializers"
```
    
Limitations
===========

- Only case classes supported

Examples
========

For more examples, please, check out 
[SerializerMacroSpec](https://github.com/evolution-gaming/kryo-macros/tree/master/macros/src/test/scala/com/evolutiongaming/kryo/SerializerMacroSpec.scala)

Benchmarks
==========

Run benchmarks
```sh
sbt -no-colors clean 'benchmark/jmh:run -prof gc .*SerializerBenchmark.*' >results.txt
```
