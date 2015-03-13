package me.zhihan.jacoco

import org.scalatest.FunSuite

import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode

import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceMethodVisitor

import me.zhihan.jacoco.internal._
import org.jacoco.core.internal.flow.MethodProbesAdapter

class MapperTest extends FunSuite {
  def linearSeqMethod = {
    val method = new MethodNode()
    method.visitLineNumber(1001, new Label())
    method.visitInsn(Opcodes.NOP)
    method.visitLineNumber(1002, new Label())
    method.visitInsn(Opcodes.RETURN)
    method
  }

  test("Sample use of tracing") {
    val method = linearSeqMethod
    val printer = new Textifier()
    val visitor = new TraceMethodVisitor(printer)
    method.accept(visitor)

    val s = printer.getText

    assert(s.size() > 4)
  }

  def sample1 {
    val idGen = new MyIdGenerator()
    val methodAdapter = new MethodProbesAdapter(
      new MethodProbesMapper(), idGen)

    val method = linearSeqMethod
    method.accept(methodAdapter)
  }
}
