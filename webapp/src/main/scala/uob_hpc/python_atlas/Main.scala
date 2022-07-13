package uob_hpc.python_atlas

import com.raquo.airstream.core.Observable
import com.raquo.airstream.state.{Val, Var}
import deckgl.*
import org.scalajs
import org.scalajs.dom
import org.scalajs.dom.*
import org.scalajs.dom.html.Canvas
import org.scalajs.dom.window.document
import typings.quickScore.mod
import typings.quickScore.mod.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.math.Ordered.orderingToOrdered
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.{JSGlobal, JSImport}
import scala.scalajs.js.{JSON, Object, UndefOr}

enum Highlight    { case OnClick, OnHover, Never        }
enum Picking      { case Node, Edge                     }
enum SearchMethod { case QuickScore, Substring          }
enum SortMethod   { case Name, Dependencies, Dependents }
enum SortOrder    { case Asc, Desc                      }

class AtlasOptions(
    val allSubdir: Observable[js.Array[String]],
    val allChannel: Observable[js.Array[String]],
    val allMarker: Observable[js.Array[String]],
    val maxRenderResolutionRatio: Val[Double] = Val(dom.window.devicePixelRatio),
    val renderResolutionRatio: Var[Double] = Var(initial = dom.window.devicePixelRatio.min(1.0)),
    val highlighting: Var[Highlight] = Var(initial = Highlight.OnClick),
    val picking: Var[Set[Picking]] = Var(initial = Set(Picking.Node)),
    val selectedSubdirs: Var[Set[String]] = Var(initial = Set.empty),
    val selectedChannels: Var[Set[String]] = Var(initial = Set.empty),
    val selectedMarker: Var[Set[String]] = Var(initial = Set.empty),
    val show: Var[Boolean] = Var(initial = true)
)

class State(
    val layout: Var[Option[JsAtlasLayout]] = Var(initial = None),
    val pypiNames: Var[js.Array[String]] = Var(initial = js.Array())
)(
    val options: AtlasOptions = AtlasOptions(
      allSubdir =  layout.signal.map(_.map(_.subdirs).getOrElse(js.Array())),
      allChannel =layout.signal.map(_.map(_.channels).getOrElse(js.Array())),
      allMarker = layout.signal.map(_.map(_.markers).getOrElse(js.Array()))
    ),
//    val cachedEdges: Observable[Option[js.Array[(AtlasEntry, AtlasEntry)]]] = data.signal.map(
//      _.map(l => time("Edges")(l.nodes.flatMap(head => head.tails.map(tailIdx => head -> l.nodes(tailIdx))).toJSArray))
//    ),
    val searchString: Var[String] = Var(initial = "numpy"),
    val searchMethod: Var[SearchMethod] = Var(initial = SearchMethod.Substring),
    val sortMethod: Var[SortMethod] = Var(initial = SortMethod.Dependents),
    val sortOrder: Var[SortOrder] = Var(initial = SortOrder.Asc)
)

def fetchJson[A](url: String): Future[A] = fetch(url).toFuture.flatMap(_.json().toFuture).map(_.asInstanceOf[A])

@JSImport("bulma/css/bulma.min.css", JSImport.Namespace)
@js.native
object Bulma extends js.Object

@JSImport("@fortawesome/fontawesome-free/css/all.css", JSImport.Namespace)
@js.native
object FontAwesomeCSS extends js.Object

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom

def mkCheckBox[A](x: A, xs: Var[Set[A]], f: A => String, modifiers: Modifier[Input]*) = label(
  cls := "mr-1",
  input(
    modifiers,
    typ("checkbox"),
    controlled(
      checked <-- xs.signal.map(_.contains(x)),
      onClick.mapToChecked --> xs.updater[Boolean] { case (xs, v) => if (v) xs + x else xs - x }
    )
  ),
  f(x)
)

def mkAtlasOptionsElem(options: AtlasOptions) = div(
  cls := "p-2",
  display.flex,
  flexDirection.column,
  alignItems.flexEnd,
  position.absolute,
  top             := "16px",
  right           := "16px",
  cls             := "",
  backgroundColor := "rgba(30, 30, 100, 0.8)",
  color           := "white",
  a(
    color      := "white",
    fontFamily := "monospace",
    onClick --> options.show.updater((v, _) => !v),
    child.text <-- options.show.signal.map(shown => s"Options [${if (shown) "-" else "+"}] ")
  ),
  table(
    display <-- options.show.signal.map(show => if (show) "" else "none"),
    tbody(
      tr(
        td("Channels"),
        td(
          children <-- options.allChannel.map {
            _.toList.sorted.map(mkCheckBox(_, options.selectedChannels, identity))
          }
        )
      ),
      tr(
        td("Subdirs"),
        td(
          children <-- options.allSubdir.map { cs =>
            val trs = cs.zipWithIndex
              .map {
                case (c @ s"$os-$arch", i) => os -> Set((arch, c -> i))
                case (x, i)                => x  -> Set((x, x -> i))
              }
              .groupMapReduce(_._1)(_._2)(_ ++ _)
              .toList
              .sortBy(_._2.size)
              .flatMap {
                case (os, as) if as.size == 1 =>
                  tr(
                    td(),
                    td(
                      mkCheckBox(os, options.selectedSubdirs, identity),
                      backgroundColor := mkColour(as.head._2._2).hexString
                    )
                  ) :: Nil
                case (os, as) =>
                  def mkRow(xs: List[(String, (String, Int))]) = xs.map { case (arch, c -> i) =>
                    println(s"$c = $i")
                    td(mkCheckBox(c, options.selectedSubdirs, _ => arch), backgroundColor := mkColour(i).hexString)
                  }
                  as.toList.sorted.sliding(2, 2).toList match {
                    case Nil => Nil
                    case r @ (x :: xs) =>
                      tr(td(os, rowSpan := r.size) :: mkRow(x)) :: xs.map(x => tr(mkRow(x)))
                  }

              }
            table(tbody(trs)) :: Nil
          }
        )
      ),
      tr(
        td("Markers"),
        td(
          table(
            tbody(
              children <-- options.allMarker.map { xs =>
                xs.toList.sorted
                  .sliding(2, 2)
                  .map { xs =>
                    tr(xs.sorted.map(x => td(mkCheckBox(x, options.selectedMarker, identity))))
                  }
                  .toSeq
              }
            )
          )
        )
      ),
      tr(
        td("Highlight"),
        td(
          select(
            cls := "mr-1",
            Highlight.values.toSeq.map(x => option(x.toString)),
            controlled(
              value <-- options.highlighting.signal.map(_.toString),
              onChange.mapToValue.map(Highlight.valueOf(_)) --> options.highlighting.writer
            )
          ),
          Picking.values.toSeq.map {
            mkCheckBox(
              _,
              options.picking,
              _.toString,
              disabled <-- options.highlighting.signal.map(_ == Highlight.Never)
            )
          }
        )
      ),
      tr(
        td("Resolution"),
        td(
          input(
            verticalAlign.middle,
            cls     := "mx-1",
            typ     := "range",
            minAttr := "0.1",
            maxAttr <-- options.maxRenderResolutionRatio.map(_.toString),
            stepAttr := "0.1",
            controlled(
              value <-- options.renderResolutionRatio.signal.map(_.toString),
              onInput.mapToValue.map { x =>
                println(s"$x !")
                x.toDoubleOption.getOrElse(1.0)
              } --> options.renderResolutionRatio.writer
            )
          ),
          child.text <-- options.renderResolutionRatio.signal.combineWithFn(options.maxRenderResolutionRatio) {
            case (x, max) => s"${((x / max) * 100.0).round}%"
          }
        )
      )
    )
  )
)

inline def consume(xs: Any*): Unit = { val _ = xs; }

def mkAtlasLayers(layout: JsAtlasLayout, enabledChannels: Set[String], enabledSubdirs : Set[String]): js.Array[Layer[?]] = {
  println("Layout!")

  console.log(layout.nodes(0))
//  println(s"Layout! ${}")

  val edges = timed("edge") {
    layout.nodes.flatMap(head => head.dependencyIndices.map(i => head -> layout.nodes(i)))
  }

  val subdirToIndex = layout.subdirs.zipWithIndex.toMap
  val enabledSubdirIndices = enabledSubdirs.map(subdirToIndex(_))

  val channelToIndex        = layout.channels.zipWithIndex.toMap
  val enabledChannelIndices = enabledChannels.map(channelToIndex(_))

  def show(x : JsAtlasEntry)= x.channelIndices.exists(enabledChannelIndices.contains(_))

  val s = new EllipseScatterplotLayer[JsAtlasEntry](new EllipseScatterplotLayerProps {
    override val coordinateSystem = CoordinateSystem.CARTESIAN
    override val radiusUnits      = "common" //
    override val lineWidthUnits   = "pixels" // screen space
    override val getLineWidth     = 1
    override val stroked          = true
    //        override val radiusScale      = 30
    override val radiusMinPixels = 2
    override val radiusMaxPixels = 1000
    override val getPosition     = _.position
    override val getRadius       = x => x.size.width * 30
    override val getRadiusRatio  = x => x.size.width / x.size.height
    override val getFillColor    = x => {

      if (show(x)) {
        x.subdirIndices.filter(enabledSubdirIndices.contains(_)).map(mkColour(_)).reduceOption(_ mix _).getOrElse(Colour(255, 255, 255)).rgb
      } else {
        js.Array(0, 0, 0, 0)
      }


    }
    override val getLineColor    = x => {
      if(show(x)){
        js.Array(255, 255, 255, 100)
      }else{
        js.Array(0, 0, 0, 0)
      }
    }
    override val pickable        = true
    override val data            = layout.nodes
    override val dataComparator  = (_,_) => false
  })

  val l = new LineLayer[(JsAtlasEntry, JsAtlasEntry)](new LineLayerProps {
    override val coordinateSystem  = CoordinateSystem.CARTESIAN
    override val widthUnits        = "common" //
    override val widthScale        = 1        //
    override val data              = edges
    override val getColor          = {
      case (x, y) if show(x) && show(y)=> js.Array(150, 150, 150, 200)
      case _ => js.Array(0, 0, 0, 0)
    }
    override val getWidth          = 0.5
    override val getSourcePosition = x => x._1.position
    override val getTargetPosition = x => x._2.position

  })

  val t = new TextLayer[JsAtlasEntry](new TextLayerProps {
    override val coordinateSystem = CoordinateSystem.CARTESIAN
    override val sizeUnits        = "common"
    override val fontWeight       = 600
    override val fontFamily       = "Sans-Serif"
    override val sizeMaxPixels    = 64
    override val sizeMinPixels    = 1
    override val getText          = _.name
    override val getPosition      = _.position
    override val getSize =
      x =>
        x.size.width
          .max(
            x.size.height
          ) * 2.0 / (x.name.length / 40.0)
    override val getColor = x =>  js.Array(0, 0, 0, 255)
    override val data     = layout.nodes
    override val dataComparator  = (_,_) => false
  })
  js.Array(l, s, t)
}

def mkNavBar() = nav(
  cls        := "navbar",
  role       := "navigation",
  aria.label := "main navigation",
  boxShadow  := "0px 2px 2px rgba(0,0,0,0.2)",
  zIndex     := 0,
  div(
    cls := "navbar-brand",
    a(
      cls  := "navbar-item",
      href := "",
      "Python Atlas"

      //          img(src := "https://bulma.io/images/bulma-logo.png", width := "112", height := "28")
    ),
    a(cls := "navbar-item", "Statistics"),
    a(cls := "navbar-item", "API"),
    a(cls := "navbar-item", "About")
  )
)
object Main {

  @main def main: Unit = {

    // Keep references to these
    consume(Bulma, FontAwesomeCSS)

    val globalStylesheet = scalajs.dom.document.createElement("style")
    globalStylesheet.textContent = """
      |.sidebar {
      |  width: 390px;
      |}
      |
      |@media screen and (max-width: 800px) {
      |  .sidebar {
      |    width: 280px;
      |  }
      |}
      |
      |@media screen and (max-width: 450px) {
      |  .sidebar {
      |    width: 100vw;
      |  }
      |}
      |
      |""".stripMargin
    scalajs.dom.document.head.append(globalStylesheet)

    val state = State()()

    val rootElement = div(
      height := "100vh",
      width  := "100vw",
      display.flex,
      flexDirection.column,
      alignItems.stretch,
      mkNavBar(),
      div(
        display.flex,
        flexGrow := 1,
        position.relative,
        overflow.hidden,
        SearchBar(state),
        div(
          display.flex,
          position.absolute,
          top             := "0",
          bottom          := "0",
          left            := "0",
          right           := "0",
          cls             := "",
          backgroundColor := "rgba(10, 10, 10, 1)",
          canvas(
            idAttr   := "viewport",
            flexGrow := 1,
            onMountUnmountCallbackWithState[ReactiveHtmlElement[Canvas], Deck](
              mount = ctx => {
                val deck = new Deck(new DeckProps {

                  override val canvas = ctx.thisNode.ref
                  override val style  = js.Dynamic.literal(position = "relative")
                  override val views = OrthographicView(new OrthographicViewProps {
                    override val flipY: UndefOr[Boolean] = true
                  })
                  override val id              = "a"
                  override val layers          = js.Array()
                  override val controller      = js.Dynamic.literal(inertia = true)
                  override val useDevicePixels = false

                  override val onClick = (a, b) => {
                    console.log(a, b)
                    if (a.picked) {
                      println(a.`object`)
                    }
                    false
                  }
                  //      override val getTooltip      = o => o.map { o =>
                  //        if (!js.isUndefined(o)) {
                  //          console.log(o)
                  //          o.asInstanceOf[AtlasEntry].name
                  //        }
                  //      }
                })

                fetchJson[js.Array[String]]("./pypi_names.json").foreach(state.pypiNames.set(_))
                fetchJson[JsAtlasLayout]("./atlas_layout.json").foreach(l => state.layout.set(Some(l)))

                state.layout.signal.foreach(_.foreach { l =>
                  deck.setProps(new DeckProps {
                    override val layers = mkAtlasLayers(l, Set.empty, Set.empty)
                    override val initialViewState = js.Dynamic.literal(
                      target = js.Array(l.maxX / 2, l.maxY / 2, 0),
                      zoom = -3.5,
                      minZoom = -5,
                      maxZoom = 1
                    )
                  })

                })(ctx.owner)

                state.layout.signal
                  .combineWithFn(
                    state.options.renderResolutionRatio,
                    state.options.selectedChannels,
                    state.options.selectedSubdirs,
                    state.options.picking,
                    state.options.highlighting
                  ) { case (layout, r, cs, ss, p, h) =>
                    println("Bind2")
                    layout.map { layout =>
                      timed("bind") {
                        new DeckProps {
                          override val layers          = mkAtlasLayers(layout = layout, enabledChannels = cs, enabledSubdirs = ss)
                          override val useDevicePixels = r
                        }
                      }
                    }
                  }
                  .foreach(_.foreach(deck.setProps(_)))(ctx.owner)

                deck
              },
              unmount = (node, state) => state.foreach(_.finalize())
            )
          )
        ),
        mkAtlasOptionsElem(state.options)
      )
    )
    render(dom.document.querySelector("body"), rootElement)
  }
}
