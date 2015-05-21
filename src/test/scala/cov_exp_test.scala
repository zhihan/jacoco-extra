package me.zhihan.jacoco.internal

import org.scalatest.FunSuite
import scala.collection.mutable.ArrayBuffer

class CovExpTest extends FunSuite {
  test("Create branch exp") {
    val a = ProbeExp(1)
    val expected = BranchExp(ArrayBuffer(a))
    assert(expected === a.branchExp)

    val b = expected
    val expected2 = BranchExp(ArrayBuffer(b))
    assert(expected2 === b.branchExp)
  }

  test("BranchExp actions") {
    val a = ProbeExp(1)
    val b = a.branchExp
    val c = ProbeExp(2)
    val expected = BranchExp(ArrayBuffer(a, c))

    val id = b.append(c)
    assert(expected === b && id == 1)

    val d = ProbeExp(3)
    b.update(0, d)
    val expected2 = BranchExp(ArrayBuffer(d, c))
    assert(expected2 == b)

    b.join(expected)
    assert(b === BranchExp(ArrayBuffer(d, c, a, c)))
  }

  test("Construction") {
    val a = ProbeExp(1)
    val b = a.branchExp
    val c = ProbeExp(2)
    val expected = BranchExp(ArrayBuffer(a, c))

    val id = b.append(c)
    assert(expected === b && id == 1)

    val d = ProbeExp(3)
    b.update(0, d)
    val expected2 = BranchExp(ArrayBuffer(d, c))
    assert(expected2 == b)

    b.join(expected)
    assert(b === BranchExp(ArrayBuffer(d, c, a, c)))
  }

  test("Eval") {
    val a = ProbeExp(1)
    val b = a.branchExp
    val c = ProbeExp(2)
    val combine = BranchExp(ArrayBuffer(a, c))
    val probes = Array(false, true, false)
    assert(CovExp.evaluate (probes) (a) === true)
    assert(CovExp.evaluate (probes) (c) === false)

    assert(CovExp.evaluate (probes) (combine) == true)
  }

}
