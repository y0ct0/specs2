package org.specs2
package specification

import execute._

/**
 * A Fragment is a piece of a specification. It can be a piece of text, an action or 
 * an Example
 */
sealed trait Fragment {
  def matches(s: String) = true
}
case class SpecStart(name: String) extends Fragment {
  override def matches(s: String) = name matches s
}
case class SpecEnd(name: String) extends Fragment {
  override def matches(s: String) = name matches s
}
case class Group(fragments: Seq[Fragment])
case class Text(t: String) extends Fragment {
  override def matches(s: String) = t.matches(s)
}
case class Example(desc: String = "", body: () => Result) extends Fragment with Executable { 
  def execute = body()
  override def matches(s: String) = desc.matches(s)
  override def toString = "Example("+desc+")"
}
case class Step(action: () => Result) extends Fragment with Executable {
  def execute = action()
  override def toString = "Step"
}

/**
 * Those standard Fragments are used to format the specification text:
 *  * End() can be used to "reset" the indentation of text  
 *  * Br() can be used to insert a newline  
 *  * Par() can be used to insert 2 newlines  
 */
private[specs2]
object StandardFragments {
  case class End() extends Fragment
  case class Par() extends Fragment
  case class Br() extends Fragment
}

