package me.zhihan.jacoco

import org.scalatest.FunSuite 

abstract class MyI {
  def name: String
}

class MyName extends MyI {
  def name = "Anonymous"
}

class HelperTests extends FunSuite {
  test("Should be able to instrument") {
    val a = Helper.instrument(classOf[MyName].getName())
    assert(a.size > 10)
  }

  test("Workflow for instrumentation") {
    val className = classOf[MyName].getName()
    val bytes = Helper.instrument(className)
    Helper.addDefinition(className, bytes)
    val runtimeData = Helper.start
    val obj = Helper.newInstance(className).asInstanceOf[MyI]
    assert(obj.name === "Anonymous")
    Helper.shutdown
  }
}

