package deckgl

import jdk.incubator.vector.IntVector
import org.scalajs.dom.{HTMLCanvasElement, HTMLElement, MouseEvent}
import uob_hpc.python_atlas.{Coordinate, Dimension, ValueOrOpt}

import scala.scalajs.js
import scala.scalajs.js.Object
import scala.scalajs.js.annotation.{JSGlobal, JSImport}

@js.native
trait View extends js.Object

@JSImport("@deck.gl/core", "OrthographicView")
@js.native
class OrthographicView(props: OrthographicViewProps) extends View {}
trait OrthographicViewProps {
  val flipY: js.UndefOr[Boolean] = js.undefined
  val near: js.UndefOr[Double]   = js.undefined
  val far: js.UndefOr[Double]    = js.undefined
}

@JSImport("@deck.gl/core", "Deck")
@js.native
class Deck(props: DeckProps) extends js.Object {
  val layers: js.Array[Layer[?]]   = js.native
  def setProps(p: DeckProps): Unit = js.native
  // noinspection ScalaDeprecation
  override def finalize(): Unit = js.native

}

trait DeckProps extends js.Object {
  val id: js.UndefOr[String]                                                    = js.undefined
  val canvas: js.UndefOr[HTMLCanvasElement | String]                            = js.undefined
  val style: js.UndefOr[js.Object]                                              = js.undefined
  val layers: js.UndefOr[js.Array[Layer[?]]]                                    = js.undefined
  val views: js.UndefOr[js.Array[View] | View]                                  = js.undefined
  val controller: js.UndefOr[Boolean | js.Object]                               = js.undefined
  val initialViewState: js.UndefOr[js.Object]                                   = js.undefined
  val viewState: js.UndefOr[js.Object]                                          = js.undefined
  val useDevicePixels: js.UndefOr[Boolean | Double]                             = js.undefined
  val getTooltip: js.UndefOr[js.Function1[js.UndefOr[js.Any], js.UndefOr[Any]]] = js.undefined

  val onHover: js.UndefOr[js.Function2[PickingInfo, MouseEvent, Boolean]] = js.undefined
  val onClick: js.UndefOr[js.Function2[PickingInfo, MouseEvent, Boolean]] = js.undefined

}

@js.native
trait PickingInfo extends js.Object {
  val layer: Layer[?]                    = js.native
  val index: Int                         = js.native
  val picked: Boolean                    = js.native
  val `object`: js.Any                   = js.native
  val x: Double                          = js.native
  val y: Double                          = js.native
  val coordinate: js.UndefOr[Coordinate] = js.native
  val viewport: js.Object                = js.native
}

@JSImport("@deck.gl/core", "COORDINATE_SYSTEM")
@js.native
object CoordinateSystem extends js.Object {
  val CARTESIAN: CoordinateSystem      = js.native
  val LNGLAT: CoordinateSystem         = js.native
  val METER_OFFSETS: CoordinateSystem  = js.native
  val LNGLAT_OFFSETS: CoordinateSystem = js.native
}
sealed trait CoordinateSystem extends js.Any

@js.native
trait Layer[A](props: LayerProps[A]) extends js.Object {
  val componentName: String                            = js.native
  def initializeState(): Unit                          = js.native
  def getShaders(): js.Object                          = js.native
  def draw(models: LayerDrawOptions): LayerDrawOptions = js.native
  var data: js.Array[A]                                = js.native

}
trait LayerDrawOptions extends js.Object {
  val uniforms: js.Object
}

trait LayerProps[A] extends js.Object {
  val data: js.Array[A]
  val pickable: js.UndefOr[Boolean]                           = js.undefined
  val coordinateSystem: js.UndefOr[CoordinateSystem]          = js.undefined
  val dataComparator: js.UndefOr[js.Function2[A, A, Boolean]] = js.undefined

}

type UnitSystem = "meters" | "common" | "pixels"

@JSImport("@deck.gl/layers", "ScatterplotLayer")
@js.native
class ScatterplotLayer[A](props: ScatterplotLayerProps[A]) extends Layer[A](props) {}
trait ScatterplotLayerProps[A] extends LayerProps[A] {
  val radiusUnits: js.UndefOr[UnitSystem]    = js.undefined
  val lineWidthUnits: js.UndefOr[UnitSystem] = js.undefined
  val stroked: js.UndefOr[Boolean]           = js.undefined
  val radiusScale: js.UndefOr[Double]        = js.undefined
  val radiusMaxPixels: js.UndefOr[Double]    = js.undefined
  val radiusMinPixels: js.UndefOr[Double]    = js.undefined

  val getPosition: ValueOrOpt[A, Coordinate]     = js.undefined
  val getRadius: ValueOrOpt[A, Double]           = js.undefined
  val getColor: ValueOrOpt[A, js.Array[Double]]  = js.undefined
  val getFillColor: ValueOrOpt[A, js.Array[Int]] = js.undefined
  val getLineColor: ValueOrOpt[A, js.Array[Int]] = js.undefined
  val getLineWidth: ValueOrOpt[A, Double]        = js.undefined
}

@JSImport("@deck.gl/layers", "TextLayer")
@js.native
class TextLayer[A](props: TextLayerProps[A]) extends Layer[A](props) {}
trait TextLayerProps[A] extends LayerProps[A] {
  val sizeScale: js.UndefOr[Double]     = js.undefined
  val sizeUnits: js.UndefOr[UnitSystem] = js.undefined
  val sizeMaxPixels: js.UndefOr[Double] = js.undefined
  val sizeMinPixels: js.UndefOr[Double] = js.undefined
  val billboard: js.UndefOr[Boolean]    = js.undefined
  val background: js.UndefOr[Boolean]   = js.undefined

  val fontFamily: js.UndefOr[String]          = js.undefined
  val characterSet: js.UndefOr[String]        = js.undefined
  val fontWeight: js.UndefOr[String | Double] = js.undefined

  val getText: ValueOrOpt[A, String]              = js.undefined
  val getPosition: ValueOrOpt[A, Coordinate]      = js.undefined
  val getSize: ValueOrOpt[A, Double]              = js.undefined
  val getColor: ValueOrOpt[A, js.Array[Double]]   = js.undefined
  val getAngle: ValueOrOpt[A, Double]             = js.undefined
  val getTextAnchor: ValueOrOpt[A, String]        = js.undefined
  val getAlignmentBaseline: ValueOrOpt[A, String] = js.undefined

}

@JSImport("@deck.gl/layers", "LineLayer")
@js.native
class LineLayer[A](props: LineLayerProps[A]) extends Layer[A](props) {}
trait LineLayerProps[A] extends LayerProps[A] {
  val widthScale: js.UndefOr[Double]     = js.undefined
  val widthUnits: js.UndefOr[UnitSystem] = js.undefined
  val widthMaxPixels: js.UndefOr[Double] = js.undefined
  val widthMinPixels: js.UndefOr[Double] = js.undefined

  val getColor: ValueOrOpt[A, js.Array[Double]]    = js.undefined
  val getWidth: ValueOrOpt[A, Double]              = js.undefined
  val getSourcePosition: ValueOrOpt[A, Coordinate] = js.undefined
  val getTargetPosition: ValueOrOpt[A, Coordinate] = js.undefined

}
