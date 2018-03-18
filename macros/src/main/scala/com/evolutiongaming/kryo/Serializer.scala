package com.evolutiongaming.kryo

import java.time.Instant
import java.util.UUID

import com.esotericsoftware.kryo
import org.joda.time.DateTime

import scala.annotation.compileTimeOnly
import scala.collection.immutable.{BitSet, IntMap, LongMap}
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
  * Macros that generate [[kryo.Serializer]] in compile time, based on compile time reflection.
  *
  * For more info and examples @see SerializerMacroSpec.
  *
  * @author Alexander Nemish
  */
object Serializer {
  @compileTimeOnly("This method should not be used outside of make/makeMapping/makeCommon macro calls")
  def inner[A]: kryo.Serializer[A] = ???
  @compileTimeOnly("This method should not be used outside of make/makeMapping/makeCommon macro calls")
  def innerMapping[A](pf: PartialFunction[Int, A]): kryo.Serializer[A] = ???
  @compileTimeOnly("This method should not be used outside of make/makeMapping/makeCommon macro calls")
  def innerCommon[A](pf: PartialFunction[Int, kryo.Serializer[_ <: A]]): kryo.Serializer[A] = ???
  def make[A]: kryo.Serializer[A] = macro Macros.serializerImpl[A]
  def makeCommon[A](pf: PartialFunction[Int, kryo.Serializer[_ <: A]]): kryo.Serializer[A] = macro Macros.mappingSerializerImpl[A]
  def makeMapping[A](pf: PartialFunction[Int, A]): kryo.Serializer[A] = macro Macros.mappingSerializerImpl[A]

  private object Macros {
    def serializerImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[kryo.Serializer[A]] = {
      import c.universe._

      val tpe = weakTypeOf[A].dealias

      def companion(tpe: Type) = Ident(tpe.typeSymbol.companion)

      def typeArg1(tpe: Type): Type = tpe.typeArgs.head.dealias

      def typeArg2(tpe: Type): Type = tpe.typeArgs.tail.head.dealias

      def hasSingleArgPublicConstructor(tpe: Type) = tpe.decls.exists {
        case m: MethodSymbol =>
          m.isConstructor && m.paramLists.size == 1 && m.paramLists.head.size == 1 && m.isPublic
        case _ => false
      }

      def isSupportedValueClass(tpe: Type) =
        tpe <:< typeOf[AnyVal] && tpe.typeSymbol.asClass.isDerivedValueClass && hasSingleArgPublicConstructor(tpe)

      def valueClassArg(tpe: Type) = tpe.decls.head  // for value classes, first declaration is its single field value

      def valueClassArgType(tpe: Type) = valueClassArg(tpe).asMethod.returnType

      case class Reader(name: TermName, tree: Tree)
      val readers = new mutable.LinkedHashMap[Type, Reader]

      def withReaderFor(tpe: Type)(f: => Tree): Tree = {
        val readerName = readers.getOrElseUpdate(tpe, {
          val impl = f
          val name = TermName(s"r${readers.size}")
          val tree = q"""private def $name(kryo: com.esotericsoftware.kryo.Kryo, input: com.esotericsoftware.kryo.io.Input): $tpe = $impl"""
          Reader(name, tree)}).name
        q"$readerName(kryo, input)"
      }

      case class Writer(name: TermName, tree: Tree)
      val writers = new mutable.LinkedHashMap[Type, Writer]

      def withWriterFor(tpe: Type, arg: Tree)(f: => Tree): Tree = {
        val writerName = writers.getOrElseUpdate(tpe, {
          val impl = f
          val name = TermName(s"w${writers.size}")
          val tree = q"""private def $name(kryo: com.esotericsoftware.kryo.Kryo, output: com.esotericsoftware.kryo.io.Output, x: $tpe): Unit = $impl"""
          Writer(name, tree)}).name
        q"$writerName(kryo, output, $arg)"
      }

      def genCode(m: MethodSymbol, annotations: Set[String]) = {
        def findImplicitSerializer(tpe: Type): Option[Tree] = {
          val serType = c.typecheck(tq"com.esotericsoftware.kryo.Serializer[$tpe]", mode = c.TYPEmode).tpe
          val implSerializer = c.inferImplicitValue(serType)
          if (implSerializer != q"") Some(implSerializer) else None
        }

        def genWriter(tpe: Type, arg: Tree): Tree = {
          lazy val implSerializer = findImplicitSerializer(tpe)
          if (tpe.widen =:= definitions.BooleanTpe) {
            q"output.writeBoolean($arg)"
          } else if (tpe.widen =:= definitions.ByteTpe) {
            q"output.writeInt($arg.toInt)"
          } else if (tpe.widen =:= definitions.CharTpe) {
            q"output.writeInt($arg.toInt)"
          } else if (tpe.widen =:= definitions.ShortTpe) {
            q"output.writeInt($arg.toInt)"
          } else if (tpe.widen weak_<:< definitions.IntTpe) {
            q"output.writeInt($arg)"
          } else if (tpe.widen =:= definitions.LongTpe) {
            q"output.writeLong($arg)"
          } else if (tpe.widen =:= definitions.DoubleTpe) {
            q"output.writeDouble($arg)"
          } else if (tpe.widen =:= definitions.FloatTpe) {
            q"output.writeFloat($arg)"
          } else if (tpe.widen =:= typeOf[String]) {
            q"output.writeString($arg)"
          } else if (tpe.widen =:= typeOf[UUID]) {
            q"output.writeLong($arg.getMostSignificantBits); output.writeLong($arg.getLeastSignificantBits)"
          } else if (tpe =:= typeOf[BigDecimal]) {
            q"output.writeString($arg.underlying.toPlainString)"
          } else if (tpe =:= typeOf[DateTime]) {
            q"output.writeLong($arg.getMillis)"
          } else if (tpe =:= typeOf[Instant]) {
            q"output.writeLong($arg.toEpochMilli)"
          } else if (tpe =:= typeOf[FiniteDuration]) {
            q"output.writeLong($arg.toMillis)"
          } else if (tpe <:< typeOf[Option[_]]) withWriterFor(tpe, arg) {
            val t = typeArg1(tpe)
            val emptiable = annotations.contains("com.evolutiongaming.kryo.Empty")
            if (emptiable && t =:= typeOf[String])
              q"""output.writeString(if (x.isEmpty) "" else x.get)"""
            else if (emptiable && isSupportedValueClass(t) && valueClassArgType(t) =:= typeOf[String])
              q"""output.writeString(if (x.isEmpty) "" else x.get.${valueClassArg(t)})"""
            else
              q"if (x.isEmpty) output.writeInt(0) else { output.writeInt(1); ${genWriter(t, q"x.get")} }"
          } else if (tpe <:< typeOf[Either[_, _]]) withWriterFor(tpe, arg) {
            val lf = genWriter(typeArg1(tpe), q"l")
            val rf = genWriter(typeArg2(tpe), q"r")
            q"""
               x match {
                 case Left(l) => output.writeInt(0); $lf
                 case Right(r) => output.writeInt(1); $rf
               }
             """
          } else if (tpe <:< typeOf[mutable.LongMap[_]] || tpe <:< typeOf[LongMap[_]]) withWriterFor(tpe, arg) {
            val t = typeArg1(tpe)
            val f = genWriter(t, q"kv._2")
            q"val s = x.size; output.writeInt(s); if (s > 0) x.foreach { kv => output.writeLong(kv._1); $f }"
          } else if (tpe <:< typeOf[IntMap[_]]) withWriterFor(tpe, arg) {
            val t = typeArg1(tpe)
            val f = genWriter(t, q"kv._2")
            q"val s = x.size; output.writeInt(s); if (s > 0) x.foreach { kv => output.writeInt(kv._1); $f }"
          } else if (tpe <:< typeOf[mutable.Map[_, _]] || tpe <:< typeOf[Map[_, _]]) withWriterFor(tpe, arg) {
            val kf = genWriter(typeArg1(tpe), q"kv._1")
            val vf = genWriter(typeArg2(tpe), q"kv._2")
            q"val s = x.size; output.writeInt(s); if (s > 0) x.foreach { kv => $kf; $vf }"
          } else if (tpe <:< typeOf[BitSet] || tpe <:< typeOf[mutable.BitSet]) withWriterFor(tpe, arg) {
            q"val bits = x.toBitMask; output.writeInt(bits.length); output.writeLongs(bits, true)"
          } else if (tpe <:< typeOf[Iterable[_]]) withWriterFor(tpe, arg) {
            val f = genWriter(typeArg1(tpe), q"a")
            q"val s = x.size; output.writeInt(s); if (s > 0) x.foreach(a => $f)"
          } else if (isSupportedValueClass(tpe)) {
            val value = valueClassArg(tpe)
            val t = valueClassArgType(tpe)
            genWriter(t, q"$arg.$value")
          } else if (tpe.widen <:< typeOf[Enum[_]]) {
            q"output.writeString($arg.name)"
          } else if (tpe.widen <:< typeOf[Enumeration#Value]) {
            q"output.writeString($arg.toString)"
          } else if (implSerializer.isDefined) {
            q"${implSerializer.get}.write(kryo, output, $arg)"
          } else {
            q"kryo.writeClassAndObject(output, $arg)"
          }
        }

        def genReader(tpe: Type): Tree = {
          lazy val implSerializer = findImplicitSerializer(tpe)
          if (tpe.widen =:= definitions.BooleanTpe) {
            q"input.readBoolean"
          } else if (tpe.widen =:= definitions.ByteTpe) {
            q"input.readInt.toByte"
          } else if (tpe.widen =:= definitions.CharTpe) {
            q"input.readInt.toChar"
          } else if (tpe.widen =:= definitions.ShortTpe) {
            q"input.readInt.toShort"
          } else if (tpe.widen =:= definitions.IntTpe) {
            q"input.readInt"
          } else if (tpe.widen weak_<:< definitions.IntTpe) { // handle Byte, Short, Char, Int
            q"input.readInt.asInstanceOf[${tpe.widen}]"
          } else if (tpe.widen =:= definitions.LongTpe) {
            q"input.readLong"
          } else if (tpe.widen =:= definitions.DoubleTpe) {
            q"input.readDouble"
          } else if (tpe.widen =:= definitions.FloatTpe) {
            q"input.readFloat"
          } else if (tpe.widen =:= typeOf[String]) {
            q"input.readString"
          } else if (tpe.widen =:= typeOf[UUID]) {
            q"new java.util.UUID(input.readLong, input.readLong)"
          } else if (tpe =:= typeOf[BigDecimal]) {
            q"scala.math.BigDecimal(input.readString)"
          } else if (tpe =:= typeOf[DateTime]) {
            q"new org.joda.time.DateTime(input.readLong)"
          } else if (tpe =:= typeOf[Instant]) {
            q"java.time.Instant.ofEpochMilli(input.readLong)"
          } else if (tpe =:= typeOf[FiniteDuration]) {
            q"new scala.concurrent.duration.FiniteDuration(input.readLong, java.util.concurrent.TimeUnit.MILLISECONDS).toCoarsest.asInstanceOf[scala.concurrent.duration.FiniteDuration]"
          } else if (tpe <:< typeOf[Option[_]]) withReaderFor(tpe) {
            val t = typeArg1(tpe)
            val emptiable = annotations.contains("com.evolutiongaming.kryo.Empty")
            if (emptiable && t =:= typeOf[String])
              q"""
                 val rs = input.readString
                 if (rs eq null) None
                 else {
                   val s = rs.trim
                   if (s.isEmpty) None else Some(s)
                 }
               """
            else if (emptiable && isSupportedValueClass(t) && valueClassArgType(t) =:= typeOf[String])
              q"""
                 val rs = input.readString
                 if (rs eq null) None
                 else {
                   val s = rs.trim
                   if (s.isEmpty) None else Some(new $t(s))
                 }
               """
            else
              q"if (input.readInt == 0) None else Some(${genReader(t)})"
          } else if (tpe <:< typeOf[Either[_, _]]) withReaderFor(tpe) {
            val lf = genReader(typeArg1(tpe))
            val rf = genReader(typeArg2(tpe))
            q"if (input.readInt == 0) scala.util.Left($lf) else scala.util.Right($rf)"
          } else if (tpe <:< typeOf[mutable.LongMap[_]]) withReaderFor(tpe) {
            val t = typeArg1(tpe)
            val f = genReader(t)
            val comp = companion(tpe)
            q"""
               val size = input.readInt
               val map = $comp.empty[$t]
               var i = 0
               while (i < size) {
                 map.update(input.readLong, $f)
                 i += 1
               }
               map
             """
          } else if (tpe <:< typeOf[LongMap[_]]) withReaderFor(tpe) {
            val t = typeArg1(tpe)
            val f = genReader(t)
            val comp = companion(tpe)
            q"""
               val size = input.readInt
               var map = $comp.empty[$t]
               var i = 0
               while (i < size) {
                 map = map.updated(input.readLong, $f)
                 i += 1
               }
               map
             """
          } else if (tpe <:< typeOf[IntMap[_]]) withReaderFor(tpe) {
            val t = typeArg1(tpe)
            val f = genReader(t)
            val comp = companion(tpe)
            q"""
               val size = input.readInt
               var map = $comp.empty[$t]
               var i = 0
               while (i < size) {
                 map = map.updated(input.readInt, $f)
                 i += 1
               }
               map
             """
          } else if (tpe <:< typeOf[mutable.Map[_, _]]) withReaderFor(tpe) {
            val kt = typeArg1(tpe)
            val vt = typeArg2(tpe)
            val kf = genReader(kt)
            val vf = genReader(vt)
            val comp = companion(tpe)
            q"""
               val size = input.readInt
               val map = $comp.empty[$kt, $vt]
               var i = 0
               while (i < size) {
                 map.update($kf, $vf)
                 i += 1
               }
               map
             """
          } else if (tpe <:< typeOf[Map[_, _]]) withReaderFor(tpe) {
            val kt = typeArg1(tpe)
            val vt = typeArg2(tpe)
            val kf = genReader(kt)
            val vf = genReader(vt)
            val comp = companion(tpe)
            q"""
               val size = input.readInt
               var map = $comp.empty[$kt, $vt]
               var i = 0
               while (i < size) {
                 map = map.updated($kf, $vf)
                 i += 1
               }
               map
             """
          } else if (tpe <:< typeOf[BitSet] || tpe <:< typeOf[mutable.BitSet]) withReaderFor(tpe) {
            val comp = companion(tpe)
            q"""
               val len = input.readInt
               if (len > 0) $comp.fromBitMaskNoCopy(input.readLongs(len, true))
               else $comp.empty
             """
          } else if (tpe <:< typeOf[Iterable[_]]) withReaderFor(tpe) {
            val t = typeArg1(tpe)
            val f = genReader(t)
            val comp = companion(tpe)
            q"""
               val size = input.readInt
               if (size > 0) {
                 val builder = $comp.newBuilder[$t]
                 var i = 0
                 do {
                   builder += $f
                   i += 1
                 } while (i < size)
                 builder.result
               } else $comp.empty[$t]
             """
          } else if (isSupportedValueClass(tpe)) {
            val reader = genReader(valueClassArgType(tpe))
            q"new $tpe($reader)"
          } else if (tpe.widen <:< typeOf[Enum[_]]) {
            val comp = companion(tpe)
            q"$comp.valueOf(input.readString)"
          } else if (tpe.widen <:< typeOf[Enumeration#Value]) {
            val TypeRef(SingleType(_, enumSymbol), _, _) = tpe
            q"$enumSymbol.withName(input.readString)"
          } else if (implSerializer.isDefined) {
            q"${implSerializer.get}.read(kryo, input, null)"
          } else {
            q"kryo.readClassAndObject(input).asInstanceOf[$tpe]"
          }
        }

        val tpe = m.returnType.dealias
        val writer = genWriter(tpe, q"x.$m")
        val reader = genReader(tpe)
        val namedArgReader = q"$m = $reader"
        writer -> namedArgReader
      }

      if (!tpe.typeSymbol.asClass.isCaseClass) c.error(c.enclosingPosition, s"$tpe must be a case class.")

      val annotations = tpe.members.collect {
        case m: TermSymbol if m.annotations.nonEmpty => m.getter -> m.annotations.map(_.toString).toSet
      }.toMap

      def notTransient(m: MethodSymbol) = !annotations.get(m).exists(_.contains("transient"))

      val fields = tpe.members.toSeq.reverse.collect {
        case m: MethodSymbol if m.isCaseAccessor && notTransient(m) =>
          genCode(m, annotations.getOrElse(m, Set.empty))
      }

      val (write, read) = fields.unzip
      val createObject = q"new $tpe(..$read)"
      val tree =
        q"""new com.esotericsoftware.kryo.Serializer[$tpe] {
            setImmutable(true)
            def write(kryo: com.esotericsoftware.kryo.Kryo, output: com.esotericsoftware.kryo.io.Output, x: $tpe): Unit = { ..$write }
            def read(kryo: com.esotericsoftware.kryo.Kryo, input: com.esotericsoftware.kryo.io.Input, `type`: Class[$tpe]): $tpe = $createObject
            ..${readers.values.map(_.tree)}
            ..${writers.values.map(_.tree)}
        }"""

      if (c.settings.contains("print-serializers")) {
        val code = showCode(tree)
        c.info(c.enclosingPosition, s"Generated kryo.Serializer for type $tpe:\n$code", force = true)
      }
      c.Expr[kryo.Serializer[A]](tree)
    }

    def mappingSerializerImpl[A: c.WeakTypeTag](c: blackbox.Context)(pf: c.Tree): c.Expr[kryo.Serializer[A]] = {
      import c.universe._

      case class InnerSerializer(name: TermName, tree: Tree)
      val serializers = new mutable.LinkedHashMap[Tree, InnerSerializer]

      val tpe = weakTypeOf[A]
      val q"{ case ..$cases }" = pf

      val constSerializerType = c.typecheck(tq"com.evolutiongaming.kryo.ConstSerializer.type", mode = c.TYPEmode).tpe

      val updatedCases = cases collect {
        case cq"$pat => $expr.inner[$tpe]" if expr.tpe =:= typeOf[Serializer.type] =>
          val fieldName = c.freshName(tpe.tpe.toString.replace('.', '$'))
          val is = serializers.getOrElseUpdate(tpe, InnerSerializer(TermName(fieldName), q"Serializer.make[$tpe]"))
          val write = cq"t: $tpe => { output.writeInt($pat); ${is.name}.write(kryo, output, t) }"
          val read = cq"$pat => ${is.name}.read(kryo, input, null)"
          write -> read
        case cq"$pat => $expr.innerMapping[$tpe]($pf)" if expr.tpe =:= typeOf[Serializer.type] =>
          val fieldName = c.freshName(tpe.tpe.toString.replace('.', '$'))
          val is = serializers.getOrElseUpdate(tpe, InnerSerializer(TermName(fieldName), q"Serializer.makeMapping[$tpe]($pf)"))
          val write = cq"t: $tpe => { output.writeInt($pat); ${is.name}.write(kryo, output, t) }"
          val read = cq"$pat => ${is.name}.read(kryo, input, null)"
          write -> read
        case cq"$pat => $expr.innerCommon[$tpe]($pf)" if expr.tpe =:= typeOf[Serializer.type] =>
          val fieldName = c.freshName(tpe.tpe.toString.replace('.', '$'))
          val is = serializers.getOrElseUpdate(tpe, InnerSerializer(TermName(fieldName), q"Serializer.makeCommon[$tpe]($pf)"))
          val write = cq"t: $tpe => { output.writeInt($pat); ${is.name}.write(kryo, output, t) }"
          val read = cq"$pat => ${is.name}.read(kryo, input, null)"
          write -> read
        case cq"$pat => $constSerializer.apply[$tpe]($v)" if constSerializer.tpe =:= constSerializerType =>
          val write = cq"t: $tpe => output.writeInt($pat)"
          val read = cq"$pat => $v"
          write -> read
        case cq"$pat => $expr" if expr.tpe.baseClasses.contains(weakTypeOf[kryo.Serializer[A]].typeSymbol) =>
          val serializerType = expr.tpe.baseType(weakTypeOf[kryo.Serializer[A]].typeSymbol).typeArgs.head.dealias
          val write = cq"t: $serializerType => { output.writeInt($pat); $expr.write(kryo, output, t) }"
          val read = cq"$pat => $expr.read(kryo, input, null)"
          write -> read
        case read@cq"$pat => $expr" =>
          val write = cq"$expr => output.writeInt($pat)"
          write -> read
      }

      val (updatedWriteCases, updatedReadCases) = updatedCases.unzip
      val write = q"x match { case ..$updatedWriteCases}"
      val read = q"(input.readInt: @scala.annotation.switch) match { case ..$updatedReadCases }"

      val innerSerializers = serializers.map {
        case (tpe, InnerSerializer(name, tree)) => q"private val $name: com.esotericsoftware.kryo.Serializer[$tpe] = $tree"
      }
      val tree =
        q"""new com.esotericsoftware.kryo.Serializer[$tpe] {
            ..$innerSerializers
            def write(kryo: com.esotericsoftware.kryo.Kryo, output: com.esotericsoftware.kryo.io.Output, x: $tpe): Unit = { ..$write }
            def read(kryo: com.esotericsoftware.kryo.Kryo, input: com.esotericsoftware.kryo.io.Input, `type`: Class[$tpe]): $tpe = $read
        }"""
      if (c.settings.contains("print-serializers")) {
        val code = showCode(tree)
        c.info(c.enclosingPosition, s"Generated kryo.Serializer for type $tpe:\n$code", force = true)
      }
      c.Expr[kryo.Serializer[A]](tree)
    }
  }
}
