package me.zhihan.jacoco.internal

import org.scalatest.FunSuite
import org.objectweb.asm.{Opcodes, Label, ClassReader, Type}
import org.objectweb.asm.tree.{MethodNode, TryCatchBlockNode}
import org.jacoco.core.internal.flow.{MethodProbesAdapter, LabelFlowAnalyzer}

import scala.collection.mutable.Map
import java.io.InputStream

class DebugTest extends FunSuite {
  def emptyMethod = {
    val method = new MethodNode()
    method.tryCatchBlocks = new java.util.ArrayList[TryCatchBlockNode]()
    method
  }

  def linearSeqMethod = {
    val method = emptyMethod
    method.visitLineNumber(1001, new Label())
    method.visitInsn(Opcodes.NOP)
    method.visitLineNumber(1002, new Label())
    method.visitInsn(Opcodes.RETURN)
    method
  }

  def analyze(method: MethodNode) = {
    val mapper = new MethodProbesMapper()
    val methodAdapter = new MethodProbesAdapter(mapper, new MyIdGenerator())
    LabelFlowAnalyzer.markLabels(method)
    method.accept(methodAdapter)
    mapper.lineToProbes
  }

  def nullTestMethod = {
    val method = emptyMethod
    method.visitVarInsn(Opcodes.ASTORE, 2)
    val line6 = new Label()
    method.visitLineNumber(6, line6)
    println(s"Line 6 is $line6")
    method.visitVarInsn(Opcodes.ALOAD, 1)
    val l1 = new Label()
    method.visitJumpInsn(Opcodes.IFNONNULL, l1)

    val line7 = new Label()
    method.visitLineNumber(7, line7)
    println(s"Line 7 is ${line7}")
    method.visitInsn(Opcodes.ACONST_NULL)
    method.visitInsn(Opcodes.ARETURN)

    method.visitLabel(l1)
    method.visitLineNumber(10, l1)
    println(s"Line 10 is $l1")
    method.visitVarInsn(Opcodes.ALOAD, 1)
    method.visitLdcInsn("yes")
    method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
      "java/lang/String", "equals",
      "(Ljava/lang/Object;)Z")
    val l2 = new Label()
    method.visitJumpInsn(Opcodes.IFEQ, l2)

    val line11 = new Label()
    method.visitLineNumber(11, line11)
    println(s"Line 11 is $line11")
    method.visitLdcInsn("yes")
    method.visitInsn(Opcodes.ARETURN)

    method.visitLabel(l2)
    method.visitLineNumber(13, l2)
    println(s"Line 13 is $l2")
    method.visitLdcInsn("no")
    method.visitInsn(Opcodes.ARETURN)
    method
  }

  test("Debug test") {
    val result = analyze(nullTestMethod)
  }
}
