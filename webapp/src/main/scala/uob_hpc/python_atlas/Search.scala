package uob_hpc.python_atlas

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import hyperlist.HyperList
import org.scalajs.dom
import uob_hpc.python_atlas.*

import scala.scalajs.js

object Search {

  enum SearchMethod { case QuickScore, Substring          }
  enum SortMethod   { case Name, Dependencies, Dependents }
  enum SortOrder    { case Asc, Desc                      }

  class State(
      val searchString: Var[String] = Var("numpy"),
      val searchMethod: Var[SearchMethod] = Var(SearchMethod.Substring),
      val sortMethod: Var[SortMethod] = Var(SortMethod.Dependents),
      val sortOrder: Var[SortOrder] = Var(SortOrder.Asc)
  )

  private final val ResultMax = 500

  private inline def search(
      inline haystack: String,
      inline needle: String,
      inline method: SearchMethod,
      inline hit: (Double, String) => Unit
  ): Unit = method match {
    case SearchMethod.Substring =>
      if (haystack.contains(needle)) hit(1.0 / haystack.length, haystack)
    case SearchMethod.QuickScore =>
      val score =
        typings.quickScore.mod.quickScore(haystack, needle, (), (), (), typings.quickScore.mod.BaseConfig)
      if (score > 0.7) hit(score, haystack)
  }

  private def mkTag(key: String, value: String) = div(
    paddingRight := "2px",
    div(cls := "tags has-addons", span(cls := "tag is-dark", key), span(cls := "tag is-info", value))
  )

  private def mkTag(key: String) = div(
    paddingRight := "2px",
    div(cls := "tags", span(cls := "tag is-dark", key))
  )

  def apply(
      state: State,
      pypiNamesSignal: StrictSignal[js.Array[String]],
      layoutSignal: StrictSignal[Option[JsAtlasLayout]]
  ) = {
    val pypiNames   = pypiNamesSignal.map(xs => xs -> new js.Set(xs))
    val layoutNodes = layoutSignal.map(_.map(_.nodes).getOrElse(js.Array()))
    // XXX debounce to avoid UI freezes while typing
    val searchString = state.searchString.signal.changes.debounce(160).toSignal(state.searchString.now())
    // XXX small delay to prevent UI freezes immediately after selection
    val searchMethod = state.searchMethod.signal.changes.delay(32).toSignal(state.searchMethod.now())

    val searchResult = searchString.toObservable.combineWithFn(searchMethod.toObservable, pypiNames, layoutNodes) {
      case (needle, method, (pypiNames, pypiNameSet), layoutNodes) =>
        println(s"Search = ${needle}, method=$method")
//        val results = js.Array[(Double, String | (JsAtlasEntry, Boolean))]()

        val results2 = js.Map[String, (Double, Option[(JsAtlasEntry, Boolean)])]()

        val (_, pypiTime) = timed {
          if (needle.isBlank)
            pypiNames.foreach { h =>
              results2 += h -> (1.0, None)

//              results += (1.0 -> h)
            }
          else
            pypiNames.foreach(h => search(h, needle, method, (score, name) => results2 += name -> (score, None)))
        }
        val (_, layoutNodeTime) = timed {
          if (needle.isBlank)
            layoutNodes.foreach(e => results2 += e.name -> (1.0, Some((e, pypiNameSet.contains(e.name)))))
          else
            layoutNodes.foreach(e =>
              search(
                e.name,
                needle,
                method,
                (s, _) => results2 += e.name -> (s, Some((e, pypiNameSet.contains(e.name))))
              )
            )
        }

        results2.toArray.sortInPlaceBy(-_._2._1) -> (pypiTime + layoutNodeTime)

//        results.sortInPlaceBy(-_._1) -> (pypiTime + layoutNodeTime)
    }

    def mkControlRow(name: String)(xs: Modifier[Div]*) =
      span(
        display.flex,
        name,
        div(
          cls      := "control",
          flexGrow := 1,
          display.flex,
          justifyContent.spaceEvenly,
          xs
        )
      )

    val packageCountTags = div(
      cls := "field is-grouped is-grouped-multiline py-2",
      display.flex,
      justifyContent.spaceBetween,
      children <-- pypiNamesSignal.map(xs => Seq(mkTag("PyPI", xs.length.toString))),
      children <-- layoutSignal.signal.map(_.map { layout =>
        layout.channels.toSeq.zipWithIndex.map { case (name, i) =>
          mkTag(name, layout.nodes.count(_.channelIndices.contains(i)).toString)
        }
      }.getOrElse(Seq.empty))
    )

    val legendTags = mkControlRow("Legends:")(
    )

    def mkSelect[A](faIcon: String, x: Var[A], xs: Array[A], f: String => A) =
      div(
        flexGrow := 1,
        cls      := "control has-icons-left",
        div(
          cls := "select is-small is-fullwidth",
          select(
            xs.toSeq.map(x => option(x.toString)),
            controlled(
              value <-- x.signal.map(_.toString),
              onChange.mapToValue.map(f) --> x.writer
            )
          )
        ),
        span(cls := "icon is-small is-left has-text-black", i(cls := s"fas $faIcon"))
      )

    val searchInput = div(
      backgroundColor := "#ececec",
      cls             := "field p-2",
      packageCountTags,
      p(
        cls := "control has-icons-right",
        input(
          onMountFocus,
          cls         := "input",
          tpe         := "text",
          placeholder := "Search packages",
          controlled(value <-- state.searchString, onInput.mapToValue --> state.searchString.writer)
        ),
        span(cls := "icon is-small is-right", i(cls := "fas fa-search"))
      ),
      span(
        cls := "pt-1",
        display.flex,
        justifyContent.spaceAround,
        flexWrap.wrap,
        mkSelect("fa-filter", state.searchMethod, SearchMethod.values, SearchMethod.valueOf(_)),
        mkSelect("fa-sort-alpha-down", state.sortMethod, SortMethod.values, SortMethod.valueOf(_)),
        mkSelect("fa-sort", state.sortOrder, SortOrder.values, SortOrder.valueOf(_))
      ),
      span(
        cls := "content is-small",
        child.text <-- searchResult.map { (xs, elapsed) =>
          f"${xs.size} results ${if (xs.size > ResultMax) s"(showing first ${ResultMax})" else ""} in ${elapsed}%.2f s"
        }
      )
    )
    div(
      zIndex          := 1,
      boxShadow       := "2px 0px 2px rgba(0,0,0,0.2)",
      cls             := "sidebar",
      backgroundColor := "white",
      display.flex,
      flexDirection.column,
      overflow.hidden,
      searchInput,
      p(
        flexGrow := 1,
        overflow.scroll,
//        (onMountCallback { ctx =>
//          val hl = new HyperList(
//            ctx.thisNode.ref,
//            new hyperlist.Config {
//              override val itemHeight = 100
//              override val total      = 0
//              override val generate   = i => render(dom.document.createElement("div"), p(s"$i")).ref
//            }
//          )
//          searchResult
//            .map { case (xs, elapsed) =>
//              println(s"Out=${xs.size}")
//              new hyperlist.Config {
//
////                override val scrollerTagName = "div"
////                override val width           = "100%"
////                override val height          = 500
//                override val itemHeight      = 100
//                override val total           = 100
//                override val generate = i => {
//
//                  val d = dom.document.createElement("div")
//                  d.innerHTML = s"ITEM ${i}"
//                  d
////                div(s"A = $i").ref
//                }
//              }
//            }
//            .foreach { x =>
//              hl.refresh(ctx.thisNode.ref, x)
//            }(ctx.owner)
//        })

        children <-- searchResult.map { (xs, elapsed) =>
          println(s"${xs.size} in ${elapsed}s")

          def renderItem(name: String, e: Option[JsAtlasEntry], inPyPi: Boolean) = layoutSignal.now() match {
            case None => p("Layout missing (!?)")
            case Some(layout) =>
              div(
                cls := "px-2",
                span(
                  display.flex,
                  justifyContent.spaceBetween,
                  h5(cls := "title is-5", name),
                  span(
                    span(
                      cls := "tag",
                      span(
                        cls := "icon-text",
                        span(cls := "icon", i(cls := "fas fa-cookie-bite")),
                        span(e.map(_.dependents.toString).getOrElse("N/A"))
                      )
                    ),
                    span("|", color := "rgba(0,0 ,0, 0.1)"),
                    span(
                      cls := "tag",
                      span(
                        cls := "icon-text",
                        span(cls := "icon", i(cls := "fas fa-coins")),
                        span(e.map(_.dependencyIndices.size.toString).getOrElse("N/A"))
                      )
                    )
                  )
                ),
                //              if (inPyPi) img(src := s"https://img.shields.io/pypi/dm/${name}.svg?label=PyPI") else commentNode(),
//                img(src := s"https://img.shields.io/conda/dn/$c/${e.name}.svg?label=$c")

                e.map { e =>

                  p(e.description,
                    p(e.url),
                    e.channelIndices.map(layout.channels(_)).map(c => mkTag(c)),
                    e.subdirIndices.map(layout.subdirs(_)).sorted.map(s => mkTag(s))
                  )

                },

                hr(cls := "my-1")
              )
          }

          xs.take(ResultMax)
            .map {
              case (k, (score, Some((e, inPypi)))) => renderItem(e.name, Some(e), inPypi)
              case (k, (score, None))              => renderItem(k, None, true)
            }
            .toSeq
        }
      ),
      p(
        backgroundColor := "#ececec",
        cls             := "field p-2 content is-small",
        div(
          div(
            cls := "tag is-info is-light",
            span(cls := "icon-text", span(cls := "icon", i(cls := "fas fa-cookie-bite")), span("= Dependencies"))
          ),
          div(
            cls := "tag is-info is-light",
            span(cls := "icon-text", span(cls := "icon", i(cls := "fas fa-coins")), span(" = Dependents"))
          )
        )
      )
    )
  }

}
