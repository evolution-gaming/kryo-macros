package com.evolutiongaming.kryo

import java.util.concurrent.TimeUnit

import com.esotericsoftware.kryo.{Kryo, Serializer}
import com.esotericsoftware.kryo.io.{Input, Output}
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@Fork(1)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class SerializerBenchmark {
  private val kryo = new Kryo
  private val buf = new Array[Byte](1024)
  private val in = new Input(buf)
  private val out = new Output(buf)
  private val anyRefsSerializer = Serializer.make[AnyRefs]
  private val iterablesSerializer = Serializer.make[Iterables]
  private val mapsSerializer = Serializer.make[Maps]
  private val primitivesSerializer = Serializer.make[Primitives]
  private val anyRefsObj = AnyRefs("s", 1, Some("os"))
  private val iterablesObj = Iterables(List("1", "2", "3"), Set(4, 5, 6), List(Set(1, 2), Set()))
  private val mapsObj = Maps(Map("1" -> 1.1, "2" -> 2.2), Map(1 -> Map(3L -> 3.3), 2 -> Map.empty[Long, Double]))
  private val primitivesObj = Primitives(1, 2, 3, 4, bl = true, 'V', 1.1, 2.2f)

  require(writeThanReadAnyRefs() == anyRefsObj)
  require(writeThanReadIterables() == iterablesObj)
  require(writeThanReadMaps() == mapsObj)
  require(writeThanReadPrimitives() == primitivesObj)

  @Benchmark
  def writeThanReadAnyRefs(): AnyRefs = writeThanRead(anyRefsSerializer, anyRefsObj)

  @Benchmark
  def writeThanReadIterables(): Iterables = writeThanRead(iterablesSerializer, iterablesObj)

  @Benchmark
  def writeThanReadMaps(): Maps = writeThanRead(mapsSerializer, mapsObj)

  @Benchmark
  def writeThanReadPrimitives(): Primitives = writeThanRead(primitivesSerializer, primitivesObj)

  private def writeThanRead[T](s: Serializer[T], obj: T): T = {
    out.setBuffer(buf)
    kryo.writeObject(out, obj, s)
    in.setBuffer(buf)
    kryo.readObject(in, obj.getClass, s)
  }
}

case class AnyRefs(s: String, bd: BigDecimal, os: Option[String])

case class Iterables(l: List[String], s: Set[Int], ls: List[Set[Int]])

case class Maps(m: Map[String, Double], mm: Map[Int, Map[Long, Double]])

case class Primitives(b: Byte, s: Short, i: Int, l: Long, bl: Boolean, ch: Char, dbl: Double, f: Float)
