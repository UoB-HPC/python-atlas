package uob_hpc.python_atlas

def linearScale(valueIn: Double, baseMin: Double, baseMax: Double, limitMin: Double, limitMax: Double): Double =
  ((limitMax - limitMin) * (valueIn - baseMin) / (baseMax - baseMin)) + limitMin

case class Subdir(name: String)  extends AnyVal
case class Channel(name: String) extends AnyVal

trait AtlasLayout[C[_], A] extends SharedModel {
  def channels: C[String]
  def subdirs: C[String]
  def markers: C[String]
  def nodes: C[A]
  def maxX: Double
  def maxY: Double
}
object AtlasLayout {

  trait Entry[C[_], P, S] extends SharedModel {
    def name: String
    def position: P
    def size: S
    def dependents: Int
    def dependencyIndices: C[Int]
    def channelIndices: C[Int]
    def subdirIndices: C[Int]
    def markerIndices: C[Int]
  }
}
