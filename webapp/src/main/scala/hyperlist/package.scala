package hyperlist

import org.scalajs.dom.Element

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@JSImport("hyperlist", JSImport.Default)
@js.native
class HyperList(container: Element, config: Config) extends js.Object {
  def refresh(container: Element, config: Config): Unit = js.native

}

trait Config extends js.Object {

  val scrollerTagName: js.UndefOr[String] = js.undefined
  val width: js.UndefOr[Double | String]  = js.undefined
  val height: js.UndefOr[Double | String] = js.undefined

  val itemHeight: Double
  val total: Int
  val generate: js.Function1[Int, Element]

}
