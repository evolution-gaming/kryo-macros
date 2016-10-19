package com.evolutiongaming.kryo

import com.esotericsoftware.kryo
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}

/**
  * Constant Serializer. Always returns `v` value. Specially treated by [[Serializer]] macros.
  */
case class ConstSerializer[T](v: T) extends kryo.Serializer[T] {
  override def write(kryo: Kryo, output: Output, `object`: T): Unit = {}

  override def read(kryo: Kryo, input: Input, `type`: Class[T]): T = v
}
