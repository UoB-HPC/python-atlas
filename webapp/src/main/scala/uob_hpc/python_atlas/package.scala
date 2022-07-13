package uob_hpc.python_atlas

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

def timed[R](block: => R): (R, Double) = {
  val t0     = System.nanoTime()
  val result = block
  val t1     = System.nanoTime()
  result -> ((t1 - t0).toDouble / 1e9)
}

def timed[R](name: String)(block: => R): R = {
  val (r, elapsed) = timed(block)
  println(s"[$name] ${elapsed}s")
  r
}

opaque type Dimension = js.Array[Double]
object Dimension {
  def apply(x: Double, y: Double): Dimension = js.Array(x, y)
  extension (c: Dimension) {
    def width: Double  = c(0)
    def height: Double = c(1)
  }
}

opaque type Coordinate = js.Array[Double]
object Coordinate {
  def apply(x: Double, y: Double): Coordinate = js.Array(x, y)
  extension (c: Coordinate) {
    def x: Double = c(0)
    def y: Double = c(1)
  }
}

type ValueOr[A, B]    = B | js.Function1[A, B]
type ValueOrOpt[A, B] = js.UndefOr[ValueOr[A, B]]

// XXX the entries are serialised as an array to save space and parse time:
type JsAtlasLayout = AtlasLayout[js.Array, JsAtlasEntry]
class JsAtlasEntry extends js.Object with AtlasLayout.Entry[js.Array, Coordinate, Dimension] {
  private inline def self                              = this.asInstanceOf[js.Array[js.Any]]
  override inline def name: String                     = self.apply(0).asInstanceOf // [String]
  override inline def position: Coordinate             = self.apply(1).asInstanceOf // [Coordinate]
  override inline def size: Dimension                  = self.apply(2).asInstanceOf // [Dimension]
  override inline def dependents: Int                  = self.apply(3).asInstanceOf // [Int]
  override inline def dependencyIndices: js.Array[Int] = self.apply(4).asInstanceOf // [js.Array[Int]]
  override inline def channelIndices: js.Array[Int]    = self.apply(5).asInstanceOf // [js.Array[Int]]
  override inline def subdirIndices: js.Array[Int]     = self.apply(6).asInstanceOf // [js.Array[Int]]
  override inline def markerIndices: js.Array[Int]     = self.apply(7).asInstanceOf // [js.Array[Int]]
}

case class Colour(hex: Int) {
  inline def r: Int            = (hex & 0xff0000) >> 16
  inline def g: Int            = (hex & 0xff00) >> 8
  inline def b: Int            = hex & 0xff
  inline def hexString: String = String.format("#%02X%02X%02X", r, g, b)
  inline def mix(inline that: Colour): Colour = Colour(
    (r + that.r) / 2,
    (g + that.g) / 2,
    (b + that.b) / 2
  )
  inline def rgb: js.Array[Int] = js.Array(r , g , b  )
}
object Colour {
  inline def apply(r: Int, g: Int, b: Int): Colour = {
    var rgb: Int = r
    rgb = (rgb << 8) + g
    rgb = (rgb << 8) + b
    Colour(rgb)
  }
}
final val Colours = Seq(
  Colour(0x3366cc),
  Colour(0xdc3912),
  Colour(0xff9900),
  Colour(0x109618),
  Colour(0x990099),
  Colour(0x0099c6),
  Colour(0xdd4477),
  Colour(0x66aa00),
  Colour(0xb82e2e),
  Colour(0x316395),
  Colour(0x994499),
  Colour(0x22aa99),
  Colour(0xaaaa11),
  Colour(0x6633cc),
  Colour(0xe67300),
  Colour(0x8b0707),
  Colour(0x651067),
  Colour(0x329262),
  Colour(0x5574a6),
  Colour(0x3b3eac),
  Colour(0xb77322),
  Colour(0x16d620),
  Colour(0xb91383),
  Colour(0xf4359e),
  Colour(0x9c5935),
  Colour(0xa9c413),
  Colour(0x2a778d),
  Colour(0x668d1c),
  Colour(0xbea413),
  Colour(0x0c5922),
  Colour(0x743411)
)
inline def mkColour(inline n: Int) = Colours(n % (Colours.size-1))
