Kryo Macros
===========

Macros that generate com.esotericsoftware.kryo.Serializer implementations in compile time, based on compile time reflection.

How to use
===========

Add the following resolver

    Resolver.bintrayRepo("evolutiongaming", "maven")
    
Add the libraty to your dependencies list

    libraryDependencies += "com.evolutiongaming.kryo" %% "kryo-macros" % 1.0.0
    
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

    scalaOptions += "-Xmacro-settings:print-serializers"
    
Limitations
===========

- Only case classes supported
- No mutable Map's as class fields supported

Examples
========

For more examples, please, check out 
[SerializerMacroSpec](https://github.com/evolution-gaming/kryo-macros/tree/master/src/test/scala/com/evolutiongaming/kryo/SerializerMacroSpec.scala)
