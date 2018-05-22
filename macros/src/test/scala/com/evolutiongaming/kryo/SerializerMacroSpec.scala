package com.evolutiongaming.kryo

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.{kryo => k}
import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}

import scala.collection.immutable.{BitSet, IntMap, LongMap}
import scala.collection.mutable
import scala.concurrent.duration._

case class PlayerId(value: String) extends AnyVal
case class IntId(value: Int) extends AnyVal
class ValWithPrivateConstructor private(val value: String) extends AnyVal
object ValWithPrivateConstructor {
  def apply(value: String): ValWithPrivateConstructor = new ValWithPrivateConstructor(value)
}

/**
  * These are test and examples of using [[Serializer]] macros for
  * generating [[k.Serializer]] for our case classes.
  *
  * scalacOptions := Seq(..., "-Xmacro-settings:print-serializers", ...)
  *
  * First, these macros only work for case classes. Otherwise, you need to write your serializers manually.
  *
  * Below tests demonstrate what is currently supported by the serializers.
  *
  * One can use type aliases.
  *
  * When you need a field not to be serialized, annotate it with [[transient]] annotation.
  *
  * When you need an Option[String] field to be serialized as an empty String in case of None,
  * annotate it with [[Empty]] annotation.
  * This is used for legacy serializers compatibility purposes.
  *
  * @usecase
  * case class YourClass(@Empty legacyField: Option[String])
  * val s = Serializer.make[YourClass]
  *
  * Will generate:
  * {{{
  * final class $anon extends kryo.Serializer[YourClass] {
  *   setImmutable(true);
  *   def write(kryo: Kryo, output: Output, x: YourClass): Unit = output.writeString(x.legacyField.getOrElse(""));
  *   def read(kryo: Kryo, input: Input, `type`: java.lang.Class[YourClass]): YourClass = new YourClass(
  *     legacyField = Option(input.readString).map(_.trim).filter(_.nonEmpty))
  * }
  * }}}
  *
  * @author Alexander Nemish
  */
class SerializerMacroSpec extends WordSpec with Matchers {
  object SuitEnum extends Enumeration {
    type SuitEnum = Value
    val Hearts, Spades, Diamonds, Clubs = Value
  }

  lazy val kryo = new Kryo

  "Serializer" should {
    "serialize and deserialize primitives" in {
      case class Primitives(b: Byte, s: Short, i: Int, l: Long, bl: Boolean, ch: Char, double: Double, f: Float)

      verify(Serializer.make[Primitives], Primitives(1, 2, 3, 4, true, 'V', 1.1, 2.2f))
    }

    "serialize and deserialize standard types" in {
      case class Types(str: String, bd: BigDecimal, id: UUID, dt: DateTime, inst: Instant, dur: FiniteDuration)

      verify(Serializer.make[Types], Types("test", 3, new UUID(1L, 2L), DateTime.now(), Instant.now(),
        FiniteDuration(1234, TimeUnit.MILLISECONDS)))
    }

    /**
      * Value classes derived from [[AnyVal]]
      */
    "serialize and deserialize value classes" in {
      case class ValueClassTypes(id: PlayerId, intId: IntId)

      verify(Serializer.make[ValueClassTypes], ValueClassTypes(PlayerId("id"), IntId(123)))
    }

    "serialize and deserialize Options" in {
      case class Opt(a: Option[String], b: Option[Int])

      verify(Serializer.make[Opt], Opt(None, Some(1)))
    }

    "serialize and deserialize Either" in {
      case class Eith(left: Either[String, Int], right: Either[String, Int])

      verify(Serializer.make[Eith], Eith(Left("Error"), Right(42)))
    }

    "serialize and deserialize Enumeration & java.lang.Enum types" in {
      case class Enums(enum: SuitEnum.SuitEnum, javaEnum: Suit)
      verify(Serializer.make[Enums], Enums(SuitEnum.Spades, Suit.Hearts))
    }

    "serialize and deserialize Iterable types" in {
      import SuitEnum._

      case class Iter(ss: Set[SuitEnum], is: List[Int], ls: mutable.ArrayBuffer[Long])

      verify(Serializer.make[Iter], Iter(Set(Hearts, Spades, Diamonds), List(1, 2, 3), mutable.ArrayBuffer(5L, 6L)))
    }

    "serialize and deserialize Map types" in {
      case class Maps(m1: Map[Int, String], m2: mutable.HashMap[Long, Double])

      verify(Serializer.make[Maps], Maps(Map(1 -> "one"), mutable.HashMap(2L -> 2.2)))
    }

    "serialize and deserialize IntMap & LongMap types" in {
      case class IntAndLongMaps(m1: IntMap[String], m2: LongMap[Double], m3: mutable.LongMap[Int])

      verify(Serializer.make[IntAndLongMaps], IntAndLongMaps(IntMap(1 -> "one"), LongMap(2L -> 2.2), mutable.LongMap(3L -> 3)))
    }

    "serialize and deserialize BitSet types" in {
      case class BitSets(b1: BitSet, b2: mutable.BitSet)

      verify(Serializer.make[BitSets], BitSets(BitSet(1, 2, 3), mutable.BitSet(1001, 1002, 1003)))
    }

    "serialize and deserialize empty strings when option string field is annotated by Empty" in {
      case class OptionString(@Empty s: Option[String])

      case class EmptyString(s: String)

      val optionStringSerializer = Serializer.make[OptionString]
      val emptyStringSerializer = Serializer.make[EmptyString]
      verifyFromTo(optionStringSerializer, OptionString(Option("VVV")), emptyStringSerializer, EmptyString("VVV"))
      verifyFromTo(optionStringSerializer, OptionString(None), emptyStringSerializer, EmptyString(""))
      verifyFromTo(emptyStringSerializer, EmptyString("VVV"), optionStringSerializer, OptionString(Option("VVV")))
      verifyFromTo(emptyStringSerializer, EmptyString(""), optionStringSerializer, OptionString(None))
      verifyFromTo(emptyStringSerializer, EmptyString("  "), optionStringSerializer, OptionString(None))
    }

    "don't serialize and deserialize field is annotated by transient or just is not defined in constructor" in {
      case class Transient(r: String, @transient t: String = "a") {
        val ignored: String = "i" + r
      }

      val transientSerializer = Serializer.make[Transient]

      case class Required(s: String)

      val requiredSerializer = Serializer.make[Required]
      verifyFromTo(transientSerializer, Transient("VVV"), requiredSerializer, Required("VVV"))
      verifyFromTo(requiredSerializer, Required("VVV"), transientSerializer, Transient("VVV"))
    }

    "serialize and deserialize respecting type aliases" in {
      type AliasToMap = Map[String, String]
      type AliasToEither[L] = Either[L, Int]
      type AliasToOption = Option[String]
      type AliasToSet = Set[Int]
      type Id = String
      case class CustomType(a: Id)
      case class Aliases(map: AliasToMap, opt: AliasToOption, set: AliasToSet, either: AliasToEither[String], ct: CustomType)
      type As = Aliases

      type CustomSerializer = k.Serializer[CustomType]
      implicit val customSerializer: CustomSerializer = Serializer.make[CustomType]

      verify(Serializer.make[As], Aliases(Map("one" -> "1"), Some(""), Set(1), Right(1), CustomType("custom")))
    }

    /**
      * When [[Serializer]] finds an implicit [[k.Serializer]] for given field's type
      * it uses it instead of generating `out.writeAny`/`in.readAny`
      *
      * Generated serializer would be:
      * class $anon extends kryo.Serializer[Outer] {
      *   def write(kryo: Kryo, output: Output, `object`: Outer): Unit = {
      *     implicitSerializerInnerA.write(kryo, output, x.a);
      *     ManualSerializerInnerB.write(kryo, output, x.b)
      *   };
      *   def read(kryo: Kryo, input: Input, `type`: Class[Outer]): Outer = new Outer(
      *     a = implicitSerializerInnerA.read(kryo, input, null),
      *     b = ManualSerializerInnerB.read(kryo, input, null))
      * };
      */
    "serialize and deserialize using implicit serializers" in {
      case class InnerA(i: Int)
      case class InnerB(s: String)
      case class Outer(a: InnerA, b: InnerB)

      implicit val implicitSerializerInnerA = Serializer.make[InnerA]

      implicit object ManualSerializerInnerB extends k.Serializer[InnerB] {
        override def write(kryo: Kryo, output: Output, `object`: InnerB): Unit = output.writeString(`object`.s)
        override def read(kryo: Kryo, input: Input, `type`: Class[InnerB]): InnerB = InnerB(input.readString())
      }

      verify(Serializer.make[Outer], Outer(InnerA(1), InnerB("b")))
    }

    /**
      * Sometime it's useful to serialize a mapping, like below. Only simple mapping are supported.
      * For something more complex please write a serializer manually.
      *
      * Serializer generated would be:
      * {{{
      *   final class $anon extends kryo.Serializer[SuitEnum.SuitEnum] {
      *     def write(kryo: Kryo, output: Output, x: SuitEnum.SuitEnum): Unit = x match {
      *       case SuitEnum.Hearts => output.writeInt(1)
      *       case SuitEnum.Spades => output.writeInt(2)
      *       case SuitEnum.Diamonds => output.writeInt(3)
      *       case SuitEnum.Clubs => output.writeInt(4)
      *     };
      *     def read(kryo: Kryo, input: Input, `type`: Class[InnerB]): SuitEnum.SuitEnum = input.readInt match {
      *       case 1 => SuitEnum.Hearts
      *       case 2 => SuitEnum.Spades
      *       case 3 => SuitEnum.Diamonds
      *       case 4 => SuitEnum.Clubs
      *     }
      *   };
      *
      * }}}
      */
    "serialize and deserialize mappings" in {
      val s = Serializer.makeMapping[SuitEnum.SuitEnum] {
        case 1 => SuitEnum.Hearts
        case 2 => SuitEnum.Spades
        case 3 => SuitEnum.Diamonds
        case 4 => SuitEnum.Clubs
      }

      verify(s, SuitEnum.Spades)
    }

    /**
      * To serialize a ADT structure, use [[Serializer.makeCommon]] macro.
      * It take a [[PartialFunction]] from Int to [[k.Serializer]] that serializes a particular ADT constructor.
      * Right-hand side [[k.Serializer]] can be a val, def or object. Either generated or manually written.
      */
    "serialize and deserialize Algebraic Data Types using existing serializers" in {
      trait User
      case class Player(name: String) extends User
      case class Dealer(name: String) extends User

      val playerSerializer = Serializer.make[Player]
      val dealerSerializer = Serializer.make[Dealer]

      val userSerializer = Serializer.makeCommon[User] {
        case 1 => playerSerializer
        case 2 => dealerSerializer
      }

      verify(userSerializer, Player("satoshi"))
    }

    /**
      *
      *
      * When you need to serializer a case object as one of the constructors of algebraic data type,
      * (e.g. `God` user), use [[ConstSerializer]]
      * It's specially treated by [[Serializer]] macros, no boilerplay is generated.
      */
    "serialize and deserialize case objects using existing ConstSerializer" in {
      trait User
      case class Player(name: String) extends User
      case class Dealer(name: String) extends User
      case object God extends User

      val playerSerializer = Serializer.make[Player]
      val dealerSerializer = Serializer.make[Dealer]

      val userSerializer = Serializer.makeCommon[User] {
        case 0 => ConstSerializer(God)
        case 1 => playerSerializer
        case 2 => dealerSerializer
      }

      verify(userSerializer, Player("satoshi"))
    }


    /**
      * Often it's useful to declare serializers of ADT constructors inside the common serializer.
      * It's possible with [[Serializer.inner]] functions.

      * @note You can't use [[Serializer.make]] inside the macros!
      *
      * There are [[Serializer.inner]], [[Serializer.innerMapping]] and [[Serializer.innerCommon]]
      * that should be used inside [[Serializer.makeCommon]] macro, accordingly.
      */
    "serialize and deserialize Algebraic Data Types using Serializer.inner macros" in {
      sealed trait ColorType
      case object Red extends ColorType
      case object Black extends ColorType

      sealed trait AlgebraicDataType
      case class B(a: String) extends AlgebraicDataType
      case class C(a: String) extends AlgebraicDataType
      case class D(a: ColorType) extends AlgebraicDataType


      val outerBSerializer = Serializer.make[B]

      val ss = Serializer.makeCommon[AlgebraicDataType] {
        case 1 => outerBSerializer
        case 2 => Serializer.inner[C]
        case 3 => Serializer.innerMapping[D] {
          case 1 => D(Red)
          case 2 => D(Black)
        }
      }

      verify(ss, B("test"))
      verify(ss, C("test"))
      verify(ss, D(Black))
    }

    "serialize and deserialize a complex data structure using all possible crap described above" in {
      trait GameType
      case object Baccarat extends GameType
      case object Roulette extends GameType

      case class Game(gameType: GameType)

      object ActionType extends Enumeration {
        val StartBetting, StopBetting = Value
      }

      trait Command
      case class Start(g: Game) extends Command
      case object Stop extends Command
      case class Action(action: ActionType.Value) extends Command

      implicit val gameTypeSerializer = Serializer.makeMapping[GameType] {
        case 1 => Baccarat
        case 2 => Roulette
      }

      // gameTypeSerializer is used here to serialize `game` field
      val gameSerializer = Serializer.make[Game]

      // you can create an implicit def for an existing serializer of needed type
      // and Serializer.make macro fill find and use it.
      implicit def implGameSerializer = gameSerializer

      val commandSerializer = Serializer.makeCommon[Command] {
        case 1 => ConstSerializer(Stop) // use ConstSerializer for case object
        case 2 => Serializer.inner[Start] // gameSerializer is used here to serialize `game` field
        case 3 => Serializer.innerMapping[Action] {
          case 1 => Action(ActionType.StartBetting)
          case 2 => Action(ActionType.StopBetting)
        }
      }

      verify(commandSerializer, Stop)
      verify(commandSerializer, Start(Game(Baccarat)))
      verify(commandSerializer, Start(Game(Roulette)))
      verify(commandSerializer, Action(ActionType.StartBetting))
      verify(commandSerializer, Action(ActionType.StopBetting))
    }

    "serialize and deserialize values classes with private constructors using implicit serializers" in {
      case class Outer(a: ValWithPrivateConstructor)

      implicit object ManualSerializer extends k.Serializer[ValWithPrivateConstructor] {
        override def write(
          kryo: Kryo, output: Output, `object`: ValWithPrivateConstructor
        ): Unit = output.writeString(`object`.value)
        override def read(
          kryo: Kryo, input: Input, `type`: Class[ValWithPrivateConstructor]
        ): ValWithPrivateConstructor = ValWithPrivateConstructor(input.readString())
      }

      verify(Serializer.make[Outer], Outer(ValWithPrivateConstructor("a")))
    }
  }

  def verify[T <: AnyRef](s: com.esotericsoftware.kryo.Serializer[T], expected: T)(implicit m: Manifest[T]): Unit = {
    val bytes = serialize(s, expected)
    val actual = deserialize(s, bytes)
    actual shouldEqual expected
  }

  def verifyFromTo[T <: AnyRef, T2 <: AnyRef](s1: com.esotericsoftware.kryo.Serializer[T], in: T,
                                              s2: com.esotericsoftware.kryo.Serializer[T2], expectedOut: T2)
                                             (implicit m1: Manifest[T], m2: Manifest[T2]): Unit = {
    val bytes = serialize(s1, in)
    val actual = deserialize(s2, bytes)
    actual shouldEqual expectedOut
  }

  def deserialize[T <: AnyRef](s: com.esotericsoftware.kryo.Serializer[T], bytes: Array[Byte])(implicit m: Manifest[T]): T =
    kryo.readObject(new Input(bytes), m.runtimeClass.asInstanceOf[Class[T]], s)

  def serialize[T <: AnyRef](s: com.esotericsoftware.kryo.Serializer[T], obj: T): Array[Byte] = {
    val out = new Output(new ByteArrayOutputStream)
    try kryo.writeObject(out, obj, s)
    finally out.close()
    out.getBuffer
  }
}
