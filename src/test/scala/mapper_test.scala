package me.zhihan.jacoco.internal

import org.scalatest.FunSuite

import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode

import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceMethodVisitor

import org.jacoco.core.internal.flow.MethodProbesAdapter
import org.jacoco.core.internal.flow.LabelFlowAnalyzer

class MapperTest extends FunSuite {
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
    mapper
  }

  test("Linear Sequence with return map") {
    val mapper = analyze(linearSeqMethod)
    assert(mapper.lineToProbes(1001).size == 1 &&
      mapper.lineToProbes(1001).contains(0))
    assert(mapper.lineToProbes(1002).size == 1 &&
      mapper.lineToProbes(1002).contains(0))
  }

  def ifBranchMethod = {
    val method = emptyMethod
    method.visitLineNumber(1001, new Label())
    method.visitVarInsn(Opcodes.ILOAD, 1)
    val l1 = new Label()
    method.visitJumpInsn(Opcodes.IFEQ, l1)
    method.visitLineNumber(1002, new Label())
    method.visitLdcInsn("a")
    method.visitInsn(Opcodes.ARETURN)
    method.visitLabel(l1)
    method.visitLineNumber(1003, l1)
    method.visitLdcInsn("b")
    method.visitInsn(Opcodes.ARETURN)
    method
  }

  test("Simple if branch method") {
    val mapper = analyze(ifBranchMethod) 
    val result = mapper.lineToProbes
    assert(result(1001).size == 2 && 
      result(1001).contains(0) && result(1001).contains(1))
    assert(result(1002).size == 1)
    assert(result(1003).size == 1)

  }
}
