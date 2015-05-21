package me.zhihan.jacoco.internal

import scala.collection.mutable.ArrayBuffer

/** The coverage of an instruction can be evaluated by either a probe or 
 the disjunction of the coverage of several sub-branches. */
abstract class CovExp {
  /** Make a branch expression with one branch. */ 
  def branchExp: BranchExp = new BranchExp(ArrayBuffer(this))
}

case class ProbeExp(val id: Int) extends CovExp {
}

/** Expression to evaluate branch coverage. */
case class BranchExp(val branches: ArrayBuffer[CovExp]) extends CovExp {

  /** Append an CovExp as a new branch. */
  def append(e: CovExp): Int = {
    branches.append(e)
    branches.size - 1
  }

  /** Update one branch in the coverage expression. */
  def update(i: Int, e: CovExp) {
    branches(i) = e
  }

  /** Combine two instruction branch coverage expressions. */
  def join(e: BranchExp) {
    branches ++= e.branches
  }
}

object CovExp {
  def evaluate (probes: Array[Boolean]) (exp: CovExp) : Boolean = {
    val eval = evaluate (probes) _ 
    exp match {
      case ProbeExp(id) => probes(id)
      case BranchExp(branches) => branches map(eval) exists(x => x)
    }
  }
}
