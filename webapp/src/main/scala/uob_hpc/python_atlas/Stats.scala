package uob_hpc.python_atlas

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import hyperlist.HyperList
import org.scalajs.dom
import uob_hpc.python_atlas.*
import uob_hpc.python_atlas.Main.Dataset

import scala.scalajs.js
import scala.scalajs.js.Date

object Stats {

  def mkVerticalBarChart(yLabel: String)(xs: Seq[(String, Double)]) = {
    import org.nspl.*
    import org.nspl.data.VectorRow
    import org.nspl.scalatagrenderer.*

    val fontSize = scala.math.min(0.6d, 20.fts.value / xs.length).fts
    val plot = xyplot(
      (
        xs.map(_._2).zipWithIndex.map { case (x, i) => VectorRow(i.toDouble, x) },
        bar(
          horizontal = false,
          fill = HeatMapColors(max = xs.map(_._2).maxOption.getOrElse(0)),
          fillCol = 1
        ) :: Nil,
        NotInLegend
      )
    )(
      par(
        xNumTicks = 0,
        xWidth = (xs.length * 1.2).fts,
        yHeight = 15.fts,
        xLabFontSize = fontSize,
        yLabFontSize = fontSize,
        xLabDistance = 10.fts,
        xLabelRotation = 290.toRadians,
        xAxisMargin = 0.001,
        ylab = yLabel,
        bottomPadding = 15.fts,
        ynames = Nil,
        xnames = xs.zipWithIndex.map { case ((name, n), i) => i.toDouble -> s"($n) $name" }
      )
    )

    renderToScalaTag(plot.build, width = xs.length * 20)
  }

  private def mkModificationHistogramChart(xs: js.Array[JsAtlasEntry]) = {
    import org.nspl.*
    import org.nspl.data.VectorRow
    import org.nspl.scalatagrenderer.*
    val modifiedOverTime = xs
      .groupBy { e =>
        val d = new Date(e.modifiedEpochMs.map(_.toDouble).getOrElse(0.0) * 1000.0)
        d.getUTCFullYear.toInt -> d.getUTCMonth.toInt
      }
      .toSeq
      .sortBy(_._1)
      .map { case ((yyyy, mm), xs) => s"$mm-$yyyy" -> xs.size.toDouble }

    val labelSize = scala.math.min(0.6d, 20.fts.value / modifiedOverTime.length).fts
    val plot = xyplot(
      (
        modifiedOverTime.map(_._2).zipWithIndex.map { case (x, i) => VectorRow(i.toDouble, x) },
        bar(
          horizontal = false,
          fill = Color.WHITE
        ) :: Nil,
        NotInLegend
      )
    )(
      par(
        topPadding = 1.fts,
        leftPadding = 2.fts,
        bottomPadding = 1.fts,
        rightPadding = 2.fts,
        xNumTicks = 0,
        xWidth = modifiedOverTime.length.fts,
        yHeight = 15.fts,
        xLabFontSize = labelSize * 0.9,
        yLabFontSize = labelSize.*(0.9),
        xlab = "Date (MM-YYYY)",
        ylab = "Modified count",
        xLabDistance = 2.fts,
        xLabelRotation = 290.toRadians,
        xAxisMargin = 0.001,
        yAxisMargin = 0,
        ylim = Some((0, (modifiedOverTime.view.map(_._2).maxOption.getOrElse(0.0) * 1.1).round)),
        xTickLength = 0.fts,
        yNumTicks = 10,
        xnames = modifiedOverTime
          .map(_._1)
          .zipWithIndex
          .map(x => x._1 -> x._2.toDouble)
          .map(_.swap)
      )
    )
    renderToScalaTag(plot.build, width = modifiedOverTime.size * 20)
  }

  class State(
      val selectedSubdirs: Var[Set[String]] = Var(Set.empty),
      val selectedChannels: Var[Set[String]] = Var(Set.empty),
      val selectedMarkers: Var[Set[String]] = Var(Set.empty)
  )

  def apply(dataset: Dataset) = {

    val state = State()

    div(
      onMountCallback { c =>
        dataset.layout.signal.foreach {
          case Some(x) =>
            state.selectedChannels.set(x.channels.toSet)
            state.selectedSubdirs.set(x.subdirs.toSet)
            state.selectedMarkers.set(x.markers.toSet)
          case None =>
            state.selectedChannels.set(Set.empty)
            state.selectedSubdirs.set(Set.empty)
            state.selectedMarkers.set(Set.empty)
        }(c.owner)
      },
//      cls := "container",
      div(
//        cls := "content",
        children <-- dataset.layout.signal
          .combineWithFn(state.selectedChannels, state.selectedSubdirs, state.selectedMarkers) {
            case (None, _, _, _) => Seq(p("Dataset pending..."))
            case (Some(layout), selectedChannels, selectedSubdirs, selectedMarkers) =>
              val subdirsToIndex       = layout.subdirs.zipWithIndex.toMap
              val enabledSubdirIndices = selectedSubdirs.map(subdirsToIndex(_))

              val channelsToIndex       = layout.channels.zipWithIndex.toMap
              val enabledChannelIndices = selectedChannels.map(channelsToIndex(_))

              val markersToIndex       = layout.markers.zipWithIndex.toMap
              val enabledMarkerIndices = selectedMarkers.map(markersToIndex(_))

              val selected = layout.nodes.filter(e =>
                e.channelIndices.exists(enabledChannelIndices.contains(_)) &&
                  e.subdirIndices.exists(enabledSubdirIndices.contains(_))
//                e.markerIndices.exists(enabledMarkerIndices.contains(_))
              )

              def mkHorizontalChart(title: String, subtitle: String)(node: => org.scalajs.dom.Node) =
                div(
                  display.flex,
                  flexDirection.column,
                  alignItems.center,
                  h4(title),
                  span(subtitle),
                  if(selected.isEmpty) span("(No data selected.)") else {


                  div(
                    textAlign.center,
                    display.flex,
                    flexDirection.column,
                    alignItems.center,
                    overflowX.scroll,
                    width   := "100vw",
                    onMountCallback(_.thisNode.ref.append(node))
                  )
                  }
                )

              Seq(
                div(
                  cls             := "content",
                  backgroundColor := "#ececec",
                  display.flex,
                  flexDirection.row,
                  alignItems.stretch,
                  justifyContent.center,
                  flexWrap.wrap,
                  div(
                    cls := "p-4",
                    h4(s"Channels (${layout.channels.size})"),
                    layout.channels.flatMap(c => Seq(UI.checkBox(c, state.selectedChannels, identity), br()))
                  ),
                  div(
                    cls := "p-4",
                    h4(s"Markers (${layout.markers.size})"),
                    layout.markers.flatMap(m => Seq(UI.checkBox(m, state.selectedMarkers, identity), br()))
                  ),
                  div(
                    cls := "p-4",
                    h4(s"Subdirs (${layout.subdirs.size})"),
                    UI.subdirSelector(
                      layout.subdirs,
                      state.selectedSubdirs,
                      i => (padding := "0") :: Nil,
                      (padding := "0") :: Nil
                    )
                  ),
                  div(
                    cls := "p-4",
                    h4(s"Result"),
                    div(s"Selected packages: ${selected.size}"),
                  )
                ),

                mkHorizontalChart("Package last modifications over time", "")(mkModificationHistogramChart(selected)),
                hr(),
                mkHorizontalChart("Package with most dependencies", "")(
                  mkVerticalBarChart("Dependency count")(
                    selected
                      .map(e => e.name -> e.dependencyIndices.size.toDouble)
                      .sortBy(-_._2)
                      .take(62)
                      .reverse
                      .toSeq
                  )
                ),
                hr(),
                mkHorizontalChart("Package with most dependents", "")(
                  mkVerticalBarChart("Dependent count")(
                    selected
                      .map(e => e.name -> e.dependents.toDouble)
                      .sortBy(-_._2)
                      .take(62)
                      .reverse
                      .toSeq
                  )
                )

//                div(
//                  cls := "columns",
//                  div(
//                    cls := "column is-full",
//                    h3("Summary"),
//
//
//
//
//
//                    ,
//                    table(
//                      tbody(
//                        s"Packages: ${selected.size}"
//                      )
//                    )
//                  )
//                ),

              )

          }
      )
    )

  }

}
