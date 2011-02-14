package org.specs2
package form

import scala.xml._
import xml.Nodex._
import text.NotNullStrings._
import main.Arguments
import execute._
import StandardResults._

/**
 * A Cell is the Textual or Xml representation of a Form element: Field, Prop or Form.
 * A more general XmlCell is also available to be able to input any kind of Xml inside a Form
 * 
 * A Cell can be executed by executing the underlying element but also set to a specific result (success or failure).
 * This feature is used to display rows of values with were expected and found ok in Forms.
 *
 */
trait Cell extends Text with Xml with Executable {
  def setSuccess: Cell
  def setFailure: Cell
  def executeCell : Cell
}
/**
 * Base type for anything returning some text
 */
trait Text {
  def text: String = padText(None)
  def width: Int = text.size
  def padText(size: Option[Int]): String
}
/**
 * Base type for anything returning some xml
 */
trait Xml {
  def xml(implicit args: Arguments): NodeSeq
  def stacktraces(implicit args: Arguments): NodeSeq
  def colnumber: Int
}

/**
 * Simple Cell embedding an arbitrary String
 */
case class TextCell(s: String, result: Result = skipped) extends Cell {
  def padText(size: Option[Int]) = {
    size match {
      case None => s.toString
      case Some(n) => s.toString.padTo(n, ' ')
    }
  }
  def xml(implicit args: Arguments) = <td class={execute.statusName}>{s}</td>
  def stacktraces(implicit args: Arguments) = NodeSeq.Empty

  def colnumber = 1
  def execute = result
  def setSuccess = TextCell(s, success)
  def setFailure = TextCell(s, failure)
  def executeCell = this
}
/**
 * Cell embedding a Field
 */
case class FieldCell(f: Field[_], result: Option[Result] = None) extends Cell {
  def padText(size: Option[Int]) = {
    size match {
      case None => f.toString
      case Some(s) => f.toString.padTo(s, ' ')
    }
  }
  def xml(implicit args: Arguments) = {
    val executed = f.valueOrResult match {
      case Left(e)  => e
      case Right(e) => e
    }
    val executedResult = execute
    (<td style={f.labelStyles}>{f.decorateLabel(f.label)}</td> unless f.label.isEmpty) ++
     <td class={statusName(executedResult)} style={f.valueStyles}>{f.decorateValue(executed)}</td> ++
    (<td class={executedResult.statusName} onclick={"showHide("+System.identityHashCode(executedResult).toString+")"}>{executedResult.message}</td> unless
      !executedResult.isError)
  }

  private def statusName(r: Result) = r match {
    case Skipped(_, _) => "info"
    case _             => r.statusName
  }

  def stacktraces(implicit args: Arguments) = NodeSeq.Empty

  def colnumber = 3

  def execute = result.getOrElse(f.execute)
  def setSuccess = FieldCell(f, Some(success))
  def setFailure = FieldCell(f, Some(failure))
  def executeCell = FieldCell(f, result.orElse(Some(f.execute)))

}
/**
 * Cell embedding a Eff
 */
case class EffectCell(e: Effect[_], result: Option[Result] = None) extends Cell {
  def padText(size: Option[Int]) = {
    size match {
      case None => e.toString
      case Some(s) => e.toString.padTo(s, ' ')
    }
  }
  def xml(implicit args: Arguments) = {
    val executed = e.valueOrResult match {
      case Left(r)  => r
      case Right(r) => r
    }
    val executedResult = execute
    <td style={e.labelStyles} class="info">{e.decorateLabel(e.label)}</td> ++
    (<td class={executedResult.statusName} onclick={"showHide("+System.identityHashCode(executedResult).toString+")"}>{executedResult.message}</td> unless executedResult.isSuccess)
  }

  private def statusName(r: Result) = r match {
    case Skipped(_, _) => "info"
    case Success(_)    => "info"
    case _             => r.statusName
  }

  def stacktraces(implicit args: Arguments) = NodeSeq.Empty

  def colnumber = 2

  def execute = result.getOrElse(e.execute)
  def setSuccess = EffectCell(e, Some(success))
  def setFailure = EffectCell(e, Some(failure))
  def executeCell = EffectCell(e, result.orElse(Some(e.execute)))

}

/**
 * Cell embedding a Prop
 */
case class PropCell(p: Prop[_,_], result: Option[Result] = None) extends Cell {
  def padText(size: Option[Int]) = {
    size match {
      case None => p.toString
      case Some(s) => p.toString.padTo(s, ' ')
    }
  }

  def colnumber = 3

  def execute = result.getOrElse(p.execute)
  def executeCell = PropCell(p, result.orElse(Some(p.execute)))
  def setSuccess = PropCell(p, Some(success))
  def setFailure = PropCell(p, Some(failure))

  def xml(implicit args: Arguments): NodeSeq = {
    val executed = result.getOrElse(skipped)
    (<td style={p.labelStyles}>{p.decorateLabel(p.label)}</td> unless p.label.isEmpty) ++
     <td class={executed.statusName}>{p.decorateValue(p.expected.getOrElse(""))}</td> ++
    (<td class={executed.statusName} onclick={"showHide("+System.identityHashCode(executed).toString+")"}>{executed.message}</td> unless executed.isSuccess)
  }

  def stacktraces(implicit args: Arguments): NodeSeq = result match {
    case Some(e @ Error(_, _))                           => stacktraces(e)
    case Some(f @ Failure(_, _, _, _)) if args.failtrace => stacktraces(f)
    case _                                               => NodeSeq.Empty
  }

  private def stacktraces(e: Result with ResultStackTrace): NodeSeq =
    <div class="formstacktrace details" id={System.identityHashCode(e).toString}>
      {e.message.notNull+" ("+e.location+")"}
      {e.stackTrace.map(st => <div>{st}</div>)}
    </div>
}
/**
 * Cell embedding a Form
 */
class FormCell(_form: =>Form) extends Cell {
  lazy val form = _form

  /** ignore the passed size and compute the max size on each row */
  def padText(size: Option[Int]): String = {
    form.padText(size)
  }
  def xml(implicit args: Arguments) = form.toCellXml(args)
  def colnumber = if (form.rows.isEmpty) 1 else form.rows.map(_.cells.map(c => c.colnumber).sum).max

  def execute = form.execute
  def executeCell = new FormCell(form.executeForm)

  def setSuccess = new FormCell(form.setSuccess)
  def setFailure = new FormCell(form.setFailure)
  def stacktraces(implicit args: Arguments) = Form.stacktraces(form)(args)

  /**
   * @return the width of a form when inlined.
   *         It is the width of its text size minus 4, which is the size of the borders "| " and " |"
   */
  override def width = text.split("\n").map((_:String).size).max[Int] - 4
}

/** Proxy to a cell that's not evaluated right away when added to a row */
class LazyCell(_cell: =>Cell) extends Cell {
  lazy val cell = _cell
  def padText(size: Option[Int]): String = cell.padText(size)
  def xml(implicit args: Arguments) = cell.xml(args)
  def colnumber = cell.colnumber
  def execute = cell.execute
  def executeCell = cell.executeCell
  def setSuccess = cell.setSuccess
  def setFailure = cell.setFailure
  def stacktraces(implicit args: Arguments) = cell.stacktraces(args)
}
/** This cell can contain any xml */
class XmlCell(_theXml: =>NodeSeq) extends Cell {
  lazy val theXml = _theXml
  def padText(size: Option[Int]): String = theXml.text
  def xml(implicit args: Arguments) = theXml
  def colnumber = 100
  def execute = success
  def executeCell = this
  def setSuccess = this
  def setFailure = this
  def stacktraces(implicit args: Arguments) = NodeSeq.Empty
}

