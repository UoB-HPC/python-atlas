package uob_hpc.python_atlas

import com.raquo.airstream.core.Observable
import com.raquo.airstream.state.{Val, Var}
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.{HTMLTableCellElement, HTMLTableRowElement}

import scala.scalajs.js

object UI {

  def checkBox[A](x: A, xs: Var[Set[A]], f: A => String, modifiers: Modifier[Input]*) = label(
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

  def subdirSelector(
      cs: js.Array[String],
      selectedSubdirs: Var[Set[String]],
      tdMod: Option[Int] => Seq[Modifier[ReactiveHtmlElement[HTMLTableCellElement]]],
      trMod: Seq[Modifier[ReactiveHtmlElement[HTMLTableRowElement]]] = Nil
  ) = {
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
            trMod,
            td(tdMod(None)),
            td(
              UI.checkBox(os, selectedSubdirs, identity),
              tdMod(Some(as.head._2._2))
            )
          ) :: Nil
        case (os, as) =>
          def mkRow(xs: List[(String, (String, Int))]) = xs.map { case (arch, c -> i) =>
            println(s"$c = $i")
            td(
              UI.checkBox(c, selectedSubdirs, _ => arch),
              tdMod(Some(i))
            )
          }
          as.toList.sorted.sliding(2, 2).toList match {
            case Nil => Nil
            case r @ (x :: xs) =>
              tr(
                trMod,
                td(os, tdMod(None), rowSpan := r.size) :: mkRow(x)
              ) :: xs.map(x => tr(trMod, mkRow(x)))
          }
      }
    table(tbody(trs)) :: Nil
  }

}
