package me.zhihan.jacoco

import me.zhihan.jacoco.internal.{MyC, MyI}
import org.objectweb.asm.ClassReader
import org.jacoco.core.data.{ExecutionData, ExecutionDataStore}
import org.jacoco.core.internal.data.CRC64
import org.scalatest.FunSuite 

abstract class MyInter {
  def name: String
}

class MyName extends MyInter {
  def name = "Anonymous"
}

class ExecutionDataTests extends FunSuite {
  test("Load executiondata") {
    val store = new ExecutionDataStore()
    val inStream = Helper.getTargetClass(classOf[MyC].getName())
    val reader = new ClassReader(inStream)
    val classId = CRC64.checksum(reader.b)
    val ed = new ExecutionData(classId,
      classOf[MyC].getName(), Array(true, false, false, false, false, false))
    store.put(ed)

    val reporter = new CoverageReporter(store)
    reporter.analyzeClass(reader)
  }
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
    val obj = Helper.newInstance(className).asInstanceOf[MyInter]

    assert(obj.name === "Anonymous")
    Helper.shutdown
  }

  test("Report branch coverage") {
    val className = classOf[MyC].getName()
    val bytes = Helper.instrument(className)
    Helper.addDefinition(className, bytes)
    val runtimeData = Helper.start
    val obj = Helper.newInstance(className).asInstanceOf[MyI]

    println("Run test")
    obj.f(-1)

    val store = Helper.collect(runtimeData)
    val reporter = new CoverageReporter(store)
    reporter.analyzeClass(
      new ClassReader(Helper.getTargetClass(className)))

  }
}

