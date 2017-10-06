package com.evolutiongaming.kryo

import java.util.concurrent.TimeUnit

import com.esotericsoftware.kryo.{Kryo, Serializer}
import com.esotericsoftware.kryo.io.{Input, Output}
import org.openjdk.jmh.annotations._

import scala.collection.immutable.{BitSet, IntMap, LongMap}
import scala.collection.mutable

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
  private val mutableMapsSerializer = Serializer.make[MutableMaps]
  private val intAndLongMapsSerializer = Serializer.make[IntAndLongMaps]
  private val bitSetsSerializer = Serializer.make[BitSets]
  private val primitivesSerializer = Serializer.make[Primitives]
  val anyRefsObj = AnyRefs("s", 1, Some("os"))
  val iterablesObj = Iterables(List("1", "2", "3"), Set(4, 5, 6), List(Set(1, 2), Set()))
  val mapsObj = Maps(Map("1" -> 1.1, "2" -> 2.2), Map(1 -> Map(3L -> 3.3), 2 -> Map.empty[Long, Double]))
  val mutableMapsObj = MutableMaps(mutable.Map("1" -> 1.1, "2" -> 2.2),
    mutable.LinkedHashMap(1 -> mutable.OpenHashMap(3L -> 3.3), 2 -> mutable.OpenHashMap.empty[Long, Double]))
  val intAndLongMapsObj = IntAndLongMaps(IntMap(1 -> 1.1, 2 -> 2.2),
    LongMap(1L -> mutable.LongMap(3L -> 3.3), 2L -> mutable.LongMap.empty[Double]))
  val bitSetsObj = BitSets(BitSet(1, 2, 3), mutable.BitSet(1001, 1002, 1003))
  val primitivesObj = Primitives(1, 2, 3, 4, bl = true, 'V', 1.1, 2.2f)

  @Benchmark
  def writeThanReadAnyRefs(): AnyRefs = writeThanRead(anyRefsSerializer, anyRefsObj)

  @Benchmark
  def writeThanReadIterables(): Iterables = writeThanRead(iterablesSerializer, iterablesObj)

  @Benchmark
  def writeThanReadMaps(): Maps = writeThanRead(mapsSerializer, mapsObj)

  @Benchmark
  def writeThanReadMutableMaps(): MutableMaps = writeThanRead(mutableMapsSerializer, mutableMapsObj)

  @Benchmark
  def writeThanReadIntAndLongMaps(): IntAndLongMaps = writeThanRead(intAndLongMapsSerializer, intAndLongMapsObj)

  @Benchmark
  def writeThanReadBitSets(): BitSets = writeThanRead(bitSetsSerializer, bitSetsObj)

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

case class MutableMaps(m: mutable.Map[String, Double], mm: mutable.LinkedHashMap[Int, mutable.OpenHashMap[Long, Double]])

case class IntAndLongMaps(m: IntMap[Double], mm: LongMap[mutable.LongMap[Double]])

case class BitSets(b1: BitSet, b2: mutable.BitSet)

case class Primitives(b: Byte, s: Short, i: Int, l: Long, bl: Boolean, ch: Char, dbl: Double, f: Float)
