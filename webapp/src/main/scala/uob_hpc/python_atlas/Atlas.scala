package uob_hpc.python_atlas

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import deckgl.*
import org.scalajs.dom.*
import org.scalajs.dom.html.Canvas
import org.scalajs.dom.window.document
import typings.quickScore.mod.*
import uob_hpc.python_atlas.Main.Dataset

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.math.Ordered.orderingToOrdered
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.{JSON, Object, UndefOr}

object Atlas {

  case class Options(
      renderResolutionRatio: Var[Double] = Var(window.devicePixelRatio.min(1.0)),
      highlighting: Var[Options.Highlight] = Var(Highlight.OnClick),
      picking: Var[Set[Options.Picking]] = Var(Set(Picking.Node)),
      selectedSubdirs: Var[Set[String]] = Var(Set.empty),
      selectedChannels: Var[Set[String]] = Var(Set.empty),
      selectedMarker: Var[Set[String]] = Var(Set.empty),
      show: Var[Boolean] = Var(true)
  )
  object Options {
    import upickle.default.*
    enum Highlight { case OnClick, OnHover, Never }
    enum Picking   { case Node, Edge              }

    given ReadWriter[Highlight] = macroRW
    given ReadWriter[Picking]   = macroRW
    given ReadWriter[Options]   = macroRW
  }

  class State(
      val dataset: Dataset,
      val options: Options = Options(),
      val searchOptions: Search.State = Search.State()
  ) {
    val allSubdir: Observable[js.Array[String]]  = dataset.layout.signal.map(_.map(_.subdirs).getOrElse(js.Array()))
    val allChannel: Observable[js.Array[String]] = dataset.layout.signal.map(_.map(_.channels).getOrElse(js.Array()))
    val allMarker: Observable[js.Array[String]]  = dataset.layout.signal.map(_.map(_.markers).getOrElse(js.Array()))
    val maxRenderResolutionRatio: Val[Double]    = Val(window.devicePixelRatio)
  }

  private def mkOptionsElem(state: State) = div(
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
      onClick --> state.options.show.updater((v, _) => !v),
      child.text <-- state.options.show.signal.map(v => s"Options [${if (v) "-" else "+"}] ")
    ),
    table(
      display <-- state.options.show.signal.map(v => if (v) "" else "none"),
      tbody(
        tr(
          td("Channels"),
          td(
            children <-- state.allChannel.map {
              _.toList.sorted.map(UI.checkBox(_, state.options.selectedChannels, identity))
            }
          )
        ),
        tr(
          td("Subdirs"),
          td(
            children <-- state.allSubdir.map { cs =>
              UI.subdirSelector(
                cs,
                state.options.selectedSubdirs,
                _.map(x => backgroundColor := mkColour(x).hexString).toList
              )
            }
          )
        ),
        tr(
          td("Markers"),
          td(
            table(
              tbody(
                children <-- state.allMarker.map { xs =>
                  xs.toList.sorted
                    .sliding(2, 2)
                    .toSeq
                    .map { xs =>
                      tr(xs.sorted.map(x => td(UI.checkBox(x, state.options.selectedMarker, identity))))
                    }

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
              Options.Highlight.values.toSeq.map(x => option(x.toString)),
              controlled(
                value <-- state.options.highlighting.signal.map(_.toString),
                onChange.mapToValue.map(Options.Highlight.valueOf(_)) --> state.options.highlighting.writer
              )
            ),
            Options.Picking.values.toSeq.map {
              UI.checkBox(
                _,
                state.options.picking,
                _.toString,
                disabled <-- state.options.highlighting.signal.map(_ == Options.Highlight.Never)
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
              maxAttr <-- state.maxRenderResolutionRatio.map(_.toString),
              stepAttr := "0.1",
              controlled(
                value <-- state.options.renderResolutionRatio.signal.map(_.toString),
                onInput.mapToValue.map { x =>
                  println(s"$x !")
                  x.toDoubleOption.getOrElse(1.0)
                } --> state.options.renderResolutionRatio.writer
              )
            ),
            child.text <-- state.options.renderResolutionRatio.signal.combineWithFn(state.maxRenderResolutionRatio) {
              case (x, max) => s"${((x / max) * 100.0).round}%"
            }
          )
        )
      )
    )
  )

  private def mkAtlasLayers(
      layout: JsAtlasLayout,
      enabledChannels: Set[String],
      enabledSubdirs: Set[String]
  ): js.Array[Layer[?]] = {
    val edges = timed("edge") {
      layout.nodes.flatMap(head => head.dependencyIndices.map(i => head -> layout.nodes(i)))
    }
    val subdirToIndex        = layout.subdirs.zipWithIndex.toMap
    val enabledSubdirIndices = enabledSubdirs.map(subdirToIndex(_))

    val channelToIndex        = layout.channels.zipWithIndex.toMap
    val enabledChannelIndices = enabledChannels.map(channelToIndex(_))

    def show(x: JsAtlasEntry) = x.channelIndices.exists(enabledChannelIndices.contains(_))

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
      override val getFillColor = x =>
        if (show(x)) {
          x.subdirIndices
            .filter(enabledSubdirIndices.contains(_))
            .map(mkColour(_))
            .reduceOption(_ mix _)
            .getOrElse(Colour(255, 255, 255))
            .rgb
        } else {
          js.Array(0, 0, 0, 0)
        }

      override val getLineColor = x =>
        if (show(x)) {
          js.Array(255, 255, 255, 100)
        } else {
          js.Array(0, 0, 0, 0)
        }
      override val pickable       = true
      override val data           = layout.nodes
      override val dataComparator = (_, _) => false
    })

    val l = new LineLayer[(JsAtlasEntry, JsAtlasEntry)](new LineLayerProps {
      override val coordinateSystem = CoordinateSystem.CARTESIAN
      override val widthUnits       = "common"
      override val widthScale       = 1
      override val data             = edges
      override val getColor = {
        case (x, y) if show(x) && show(y) => js.Array(150, 150, 150, 200)
        case _                            => js.Array(0, 0, 0, 0)
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
      override val getColor       = x => js.Array(0, 0, 0, 255)
      override val data           = layout.nodes
      override val dataComparator = (_, _) => false
    })
    js.Array(l, s, t)
  }

  def apply(dataset: Dataset) = {

    val state = State(dataset)

    div(
      display.flex,
      flexGrow := 1,
      position.relative,
      overflow.hidden,
      Search(state.searchOptions, state.dataset.pypiNames.signal, state.dataset.layout.signal),
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

              state.dataset.layout.signal.foreach(_.foreach { l =>
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

              state.dataset.layout.signal
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
                        override val layers = mkAtlasLayers(layout = layout, enabledChannels = cs, enabledSubdirs = ss)
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
      mkOptionsElem(state)
    )
  }

}
