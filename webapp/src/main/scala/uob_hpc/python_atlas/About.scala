package uob_hpc.python_atlas

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import hyperlist.HyperList
import org.scalajs.dom.DOMList.*
import org.scalajs.dom.html.Anchor
import org.scalajs.dom.{HTMLCollection, window}
import uob_hpc.python_atlas.*

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object About {

  @JSImport("./public/about.md", JSImport.Namespace)
  @js.native
  private object AboutMD extends js.Object { def default: String = js.native }

  def apply(section: Signal[String], baseUrl: String) =
    div(
      cls := "section",
      div(
        cls := "container",
        div(
          cls := "content",
          onMountCallback { ctx =>
            ctx.thisNode.ref.innerHTML = AboutMD.default
            ctx.thisNode.ref.getElementsByTagName("a").foreach { e =>
              e.getAttribute("href") match {
                case s"#$id" => e.setAttribute("href", s"$baseUrl/$id")
                case _       => ()
              }
            }
            section
              .map(_.toLowerCase.replace(' ', '-'))
              .foreach { s =>
                if (!s.isBlank) {
                  Option(ctx.thisNode.ref.querySelector(s"#$s")).foreach(_.scrollIntoView())
                }
              }(ctx.owner)
            ()
          }
        )
      )
    )

}
