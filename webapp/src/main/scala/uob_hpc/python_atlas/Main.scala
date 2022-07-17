package uob_hpc.python_atlas

import com.raquo.airstream.core.Observable
import com.raquo.airstream.state.{Val, Var}
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.waypoint.*
import org.scalajs.dom
import org.scalajs.dom.console
import uob_hpc.python_atlas.Main.Page.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object Main {

  class Dataset(
      val layout: Var[Option[JsAtlasLayout]] = Var(None),
      val pypiNames: Var[js.Array[String]] = Var(js.Array())
  )

  sealed abstract class Page(val title: String)
  object Page {
    import upickle.default.*
    case object AtlasPage                      extends Page("")
    case class PackagePage(name: String)       extends Page(name)
    case object StatsPage                      extends Page(" - Stats")
    case class AboutPage(section: String = "") extends Page(" - About/API")
    given ReadWriter[Page]        = macroRW
    given ReadWriter[AboutPage]   = macroRW
    given ReadWriter[PackagePage] = macroRW
  }

  val router = new Router[Page](
    routes = List(
      Route.static(Page.AtlasPage, root / endOfSegments, Route.fragmentBasePath),
      Route[Page.AboutPage, String](
        _.section,
        Page.AboutPage(_),
        root / "about" / segment[String] / endOfSegments,
        Route.fragmentBasePath
      ),
      Route.static(Page.AboutPage(), root / "about" / endOfSegments, Route.fragmentBasePath),
      Route.static(Page.StatsPage, root / "stats" / endOfSegments, Route.fragmentBasePath),
      Route.onlyQuery[Page.PackagePage, String](
        _.name,
        PackagePage(_),
        (root / "package" / endOfSegments) ? param[String]("name"),
        Route.fragmentBasePath
      )
    ),
    getPageTitle = p => s"PyAtlas${p.title}",
    serializePage = upickle.default.web.write(_),
    deserializePage = upickle.default.web.read[Page](_)
  )(
    $popStateEvent = L.windowEvents.onPopState,
    owner = L.unsafeWindowOwner
  )

  def navigateTo(page: Page): Binder[HtmlElement] = Binder { el =>
    val isLinkElement = el.ref.isInstanceOf[dom.html.Anchor]
    if (isLinkElement)
      el.amend(href(router.absoluteUrlForPage(page)))
    (onClick
      .filter(ev => !(isLinkElement && (ev.ctrlKey || ev.metaKey || ev.shiftKey || ev.altKey)))
      .preventDefault
      --> (_ => router.pushState(page))).bind(el)
  }

  @JSImport("bulma/css/bulma.min.css", JSImport.Namespace)
  @js.native
  object Bulma extends js.Object

  @JSImport("@fortawesome/fontawesome-free/css/all.css", JSImport.Namespace)
  @js.native
  object FontAwesomeCSS extends js.Object

  private def mkNavBar() = nav(
    cls        := "navbar",
    role       := "navigation",
    aria.label := "main navigation",
    boxShadow  := "0px 2px 2px rgba(0,0,0,0.2)",
    div(
      cls := "navbar-brand",
      a(
        cls  := "navbar-item",
        href := "",
        "PyAtlas"
      ),
      a(
        cls := "navbar-item",
        span(cls := "icon is-medium", i(cls := "fas fa-project-diagram")),
        "Atlas",
        navigateTo(Page.AtlasPage)
      ),
      a(
        cls := "navbar-item",
        span(cls := "icon is-medium", i(cls := "fas fa-chart-line")),
        "Stats",
        navigateTo(Page.StatsPage)
      ),
      a(
        cls := "navbar-item",
        span(cls := "icon is-medium", i(cls := "fas fa-info-circle")),
        "About/API",
        navigateTo(Page.AboutPage())
      )
    )
  )

  @main def main(): Unit = {

    // XXX keep a reference to these
    val _ = Seq(Bulma, FontAwesomeCSS)

    val dataset = Dataset()

    fetchJson[js.Array[String]]("./pypi_names.json").foreach(dataset.pypiNames.set(_))
    fetchJson[JsAtlasLayout]("./atlas_layout.json").foreach(l => dataset.layout.set(Some(l)))

    val globalStylesheet = dom.document.createElement("style")
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
    dom.document.head.append(globalStylesheet)

    val pageSplitter = SplitRender[Page, HtmlElement](router.$currentPage)
      .collectSignal[PackagePage]($appPage => div("Pkg=", child.text <-- $appPage.map(_.name)))
      .collectSignal[AboutPage](p => About(p.map(_.section), router.absoluteUrlForPage(AboutPage())))
      .collectStatic(StatsPage)(Stats(dataset))
      .collectStatic(AtlasPage)(Atlas(dataset))

    render(
      dom.document.querySelector("body"),
      div(
        height := "100vh",
        width  := "100vw",
        display.flex,
        flexDirection.column,
        alignItems.stretch,
        mkNavBar(),
        child <-- pageSplitter.$view
      )
    )
  }
}
