package com.evolutiongaming.kryo

import java.time.Instant

import com.esotericsoftware.kryo
import org.joda.time.DateTime

import scala.annotation.compileTimeOnly
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
      import c.universe
      import c.universe._
      val tpe = weakTypeOf[A]

      def companion(tpe: Type) = Ident(tpe.typeSymbol.companion)

      def isValueClass(tpe: Type) = tpe <:< typeOf[AnyVal] && tpe.typeSymbol.asClass.isDerivedValueClass

      def valueClassArgType(tpe: Type) = {
        // for value classes, first class declaration is its single field value
        tpe.decls.head.asMethod.returnType
      }

      def genCode(m: MethodSymbol, annotations: Set[String]) = {
        def findImplicitSerializer(tpe: Type): Option[Tree] = {
          val serType = c.typecheck(tq"com.esotericsoftware.kryo.Serializer[$tpe]", mode = c.TYPEmode).tpe
          val implSerializer = c.inferImplicitValue(serType)
          if (implSerializer != q"") Some(implSerializer) else None
        }

        def genWriter(tpe: Type, arg: Tree): Tree = {
          lazy val implSerializer = findImplicitSerializer(tpe)
          if (tpe.widen =:= universe.definitions.BooleanTpe) {
            q"output.writeBoolean($arg)"
          } else if (tpe.widen =:= universe.definitions.ByteTpe) {
            q"output.writeInt($arg.toInt)"
          } else if (tpe.widen =:= universe.definitions.CharTpe) {
            q"output.writeInt($arg.toInt)"
          } else if (tpe.widen =:= universe.definitions.ShortTpe) {
            q"output.writeInt($arg.toInt)"
          } else if (tpe.widen weak_<:< universe.definitions.IntTpe) {
            q"output.writeInt($arg)"
          } else if (tpe.widen =:= universe.definitions.LongTpe) {
            q"output.writeLong($arg)"
          } else if (tpe.widen =:= universe.definitions.DoubleTpe) {
            q"output.writeDouble($arg)"
          } else if (tpe.widen =:= universe.definitions.FloatTpe) {
            q"output.writeFloat($arg)"
          } else if (tpe.widen =:= typeOf[String]) {
            q"output.writeString($arg)"
          } else if (tpe =:= typeOf[BigDecimal]) {
            q"output.writeString($arg.underlying().toPlainString)"
          } else if (tpe =:= typeOf[DateTime]) {
            q"output.writeLong($arg.getMillis)"
          } else if (tpe =:= typeOf[Instant]) {
            q"output.writeLong($arg.toEpochMilli)"
          } else if (tpe =:= typeOf[FiniteDuration]) {
            q"output.writeLong($arg.toMillis)"
          } else if (tpe <:< typeOf[Option[_]]) {
            val typeArg = tpe.typeArgs.head.dealias
            val emptiable = annotations.contains("com.evolutiongaming.kryo.Empty")
            if (emptiable && typeArg =:= typeOf[String])
              q"""output.writeString($arg.getOrElse(""))"""
            else if (emptiable && isValueClass(typeArg) && valueClassArgType(typeArg) =:= typeOf[String]) {
              val value = typeArg.decls.head // for value classes, first declaration is its single field value
              q"""output.writeString(if ($arg.isEmpty) "" else $arg.get.$value)"""
            } else {
              val f = genWriter(typeArg, q"a")
              q"""output.writeInt($arg.size)
                  $arg.foreach(a => $f)"""
            }
          } else if (tpe <:< typeOf[Either[_, _]]) {
            val List(lt, rt) = tpe.typeArgs
            val lf = genWriter(lt.dealias, q"l")
            val rf = genWriter(rt.dealias, q"r")
            q"""
               $arg match {
                 case Left(l) => output.writeInt(0); $lf
                 case Right(r) => output.writeInt(1); $rf
               }
             """
          } else if (tpe <:< typeOf[Map[_, _]]) {
            val List(kt, vt) = tpe.typeArgs
            val kf = genWriter(kt.dealias, q"kv._1")
            val vf = genWriter(vt.dealias, q"kv._2")
            q"""output.writeInt($arg.size)
                $arg.foreach { kv => $kf; $vf }
             """
          } else if (tpe <:< typeOf[Iterable[_]]) {
            val t = tpe.typeArgs.head.dealias
            val f = genWriter(t, q"a")
            q"""
                output.writeInt($arg.size)
                $arg.foreach(a => $f)
             """
          } else if (isValueClass(tpe)) {
            val value = tpe.decls.head // for value classes, first declaration is its single field value
            val valueTpe = valueClassArgType(tpe)
            genWriter(valueTpe, q"$arg.$value")
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
          if (tpe.widen =:= universe.definitions.BooleanTpe) {
            q"input.readBoolean"
          } else if (tpe.widen =:= universe.definitions.ByteTpe) {
            q"input.readInt.toByte"
          } else if (tpe.widen =:= universe.definitions.CharTpe) {
            q"input.readInt.toChar"
          } else if (tpe.widen =:= universe.definitions.ShortTpe) {
            q"input.readInt.toShort"
          } else if (tpe.widen =:= universe.definitions.IntTpe) {
            q"input.readInt"
          } else if (tpe.widen weak_<:< universe.definitions.IntTpe) {
            // handle Byte, Short, Char, Int
            q"input.readInt.asInstanceOf[${tpe.widen}]"
          } else if (tpe.widen =:= universe.definitions.LongTpe) {
            q"input.readLong"
          } else if (tpe.widen =:= universe.definitions.DoubleTpe) {
            q"input.readDouble"
          } else if (tpe.widen =:= universe.definitions.FloatTpe) {
            q"input.readFloat"
          } else if (tpe.widen =:= typeOf[String]) {
            q"input.readString"
          } else if (tpe =:= typeOf[BigDecimal]) {
            q"scala.math.BigDecimal(input.readString)"
          } else if (tpe =:= typeOf[DateTime]) {
            q"new org.joda.time.DateTime(input.readLong)"
          } else if (tpe =:= typeOf[Instant]) {
            q"java.time.Instant.ofEpochMilli(input.readLong)"
          } else if (tpe =:= typeOf[FiniteDuration]) {
            q"new scala.concurrent.duration.FiniteDuration(input.readLong, java.util.concurrent.TimeUnit.MILLISECONDS).toCoarsest.asInstanceOf[scala.concurrent.duration.FiniteDuration]"
          } else if (tpe <:< typeOf[Option[_]]) {
            val typeArg = tpe.typeArgs.head.dealias
            val emptiable = annotations.contains("com.evolutiongaming.kryo.Empty")
            if (emptiable && typeArg =:= typeOf[String])
              q"""Option(input.readString) map (_.trim) filter (_.nonEmpty)"""
            else if (emptiable && isValueClass(typeArg) && valueClassArgType(typeArg) =:= typeOf[String])
              q"""Option(input.readString).map(_.trim).collect { case s if s.nonEmpty => new $typeArg(s) }"""
            else {
              val f = genReader(typeArg)
              q"if (input.readInt == 0) (None: Option[$typeArg]) else Some($f)"
            }
          } else if (tpe <:< typeOf[Either[_, _]]) {
            val List(lt, rt) = tpe.typeArgs
            val lf = genReader(lt.dealias)
            val rf = genReader(rt.dealias)
            q"if (input.readInt == 0) scala.util.Left($lf) else scala.util.Right($rf)"
          } else if (tpe <:< typeOf[Map[_, _]]) {
            val List(kt, vt) = tpe.typeArgs.map(_.dealias)
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
          } else if (tpe <:< typeOf[Iterable[_]]) {
            val t = tpe.typeArgs.head.dealias
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
          } else if (isValueClass(tpe)) {
            val reader = genReader(valueClassArgType(tpe))
            q"new $tpe($reader)"
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

      def notTransient(m: MethodSymbol) = {
        !annotations.get(m).exists(as => as.contains("transient"))
      }

      val fields = tpe.members.collect {
        case m: MethodSymbol if m.isCaseAccessor && notTransient(m) =>
          genCode(m, annotations.getOrElse(m, Set()))
      }.toList.reverse

      val (write, read) = fields.unzip
      val createObject = q"new $tpe(..$read)"
      val tree =
        q"""new com.esotericsoftware.kryo.Serializer[$tpe] {
            setImmutable(true)
            def write(kryo: com.esotericsoftware.kryo.Kryo, output: com.esotericsoftware.kryo.io.Output, x: $tpe): Unit = { ..$write }
            def read(kryo: com.esotericsoftware.kryo.Kryo, input: com.esotericsoftware.kryo.io.Input, `type`: java.lang.Class[$tpe]): $tpe = $createObject
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
      val serializers = new mutable.HashMap[Tree, InnerSerializer]()

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
            def read(kryo: com.esotericsoftware.kryo.Kryo, input: com.esotericsoftware.kryo.io.Input, `type`: java.lang.Class[$tpe]): $tpe = $read
        }"""
      if (c.settings.contains("print-serializers")) {
        val code = showCode(tree)
        c.info(c.enclosingPosition, s"Generated kryo.Serializer for type $tpe:\n$code", force = true)
      }
      c.Expr[kryo.Serializer[A]](tree)
    }
  }
}
