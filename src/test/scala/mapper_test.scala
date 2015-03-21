package me.zhihan.jacoco.internal

import org.scalatest.FunSuite
import org.objectweb.asm.{Opcodes, Label}
import org.objectweb.asm.tree.{MethodNode, TryCatchBlockNode}
import org.jacoco.core.internal.flow.{MethodProbesAdapter, LabelFlowAnalyzer}

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
    mapper.lineToProbes
  }

  test("Linear Sequence with return map") {
    val result = analyze(linearSeqMethod)
    assert(result(1001).size == 1 &&
      result(1001).contains(0))
    assert(result(1002).size == 1 &&
      result(1002).contains(0))
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
    val result = analyze(ifBranchMethod) 
    assert(result(1001).size == 2 && 
      result(1001).contains(0) && result(1001).contains(1))
    assert(result(1002).size == 1)
    assert(result(1003).size == 1)
  }

  def ifBranchMergeMethod = {
    val method = emptyMethod
    method.visitLineNumber(1001, new Label())
    val l1 = new Label()
    method.visitJumpInsn(Opcodes.IFEQ, l1)
    method.visitLineNumber(1002, new Label())
    method.visitInsn(Opcodes.NOP)
    method.visitLabel(l1)
    method.visitLineNumber(1003, l1)
    method.visitInsn(Opcodes.RETURN)
    method
  }

  test("If branch with merge method") {
    val result = analyze(ifBranchMergeMethod) 
    assert(result(1001).size == 2 && 
      result(1001).contains(0) && result(1001).contains(1))
    assert(result(1002).size == 1)
    // the last line has an additional probe
    assert(result(1003).size == 1 && result(1003).contains(2))
  }

  def jumpBackwardMethod = {
    val method = emptyMethod
    method.visitLineNumber(1001, new Label())
    val l1 = new Label()
    method.visitJumpInsn(Opcodes.GOTO, l1)
    val l2 = new Label()
    method.visitLabel(l2)
    method.visitLineNumber(1002, l2)
    method.visitInsn(Opcodes.RETURN)

    method.visitLabel(l1)
    method.visitLineNumber(1003, l1)
    method.visitJumpInsn(Opcodes.GOTO, l2)
    method
  }

  test("Jump backwards method") {
    val result = analyze(jumpBackwardMethod) 
    assert(result(1001).size == 1 && result(1001).contains(0))
    assert(result(1002).size == 1 && result(1002).contains(0))
    assert(result(1003).size == 1 && result(1003).contains(0))
  }

  def jumpToFirstMethod = {
    val method = emptyMethod
    val l1 = new Label()
    method.visitLabel(l1)
    method.visitLineNumber(1001, l1)
    method.visitVarInsn(Opcodes.ALOAD, 0)
    method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
      "Foo", "test", "()Z", false)
    method.visitJumpInsn(Opcodes.IFEQ, l1)
    val l2 = new Label()
    method.visitLabel(l2)
    method.visitLineNumber(1002, l2)
    method.visitInsn(Opcodes.RETURN)
    method
  }

  test("Jump to first instruction method") {
    val result = analyze(jumpToFirstMethod)
    assert(result(1001).size == 2 &&
      result(1001).contains(0) && result(1001).contains(1))
    assert(result(1002).size == 1)
  }

  def tableSwitchMethod = {
    val method = emptyMethod
    method.visitLineNumber(1001, new Label())
    method.visitVarInsn(Opcodes.ILOAD, 1)
    val l1 = new Label()
    val l2 = new Label()
    val l3 = new Label()
    method.visitTableSwitchInsn(1, 3, l3, Array[Label](l1, l2, l3) :_*)

    method.visitLabel(l1)
    method.visitLineNumber(1002, l1)
    method.visitIntInsn(Opcodes.BIPUSH, 11)
    method.visitVarInsn(Opcodes.ISTORE, 2)
    method.visitLineNumber(1003, new Label())
    val l5 = new Label()
    method.visitJumpInsn(Opcodes.GOTO, l5)

    method.visitLabel(l2)
    method.visitLineNumber(1004, l2)
    method.visitIntInsn(Opcodes.BIPUSH, 22)
    method.visitVarInsn(Opcodes.ISTORE, 2)
    method.visitLineNumber(1005, new Label())
    method.visitJumpInsn(Opcodes.GOTO, l5)

    method.visitLabel(l3)
    method.visitLineNumber(1006, l3)
    method.visitInsn(Opcodes.ICONST_0)
    method.visitVarInsn(Opcodes.ISTORE, 2)

    method.visitLabel(l5)
    method.visitLineNumber(1007, l5)
    method.visitVarInsn(Opcodes.ILOAD, 2)

    method.visitInsn(Opcodes.IRETURN)
    method
  }

  test("Table switch with no intermediate labels") {
    val result = analyze(tableSwitchMethod)
    assert(result(1001).size == 3 &&
      result(1001).contains(0) && result(1001).contains(1) && result(1001).contains(2))
    assert(result(1007).size == 1 && result(1007).contains(3))
  }

  def tableSwitchWithMerge = {
    val method = emptyMethod
    method.visitLineNumber(1001, new Label())
    method.visitInsn(Opcodes.ICONST_0)
    method.visitVarInsn(Opcodes.ISTORE, 2)
    method.visitLineNumber(1002, new Label())
    method.visitVarInsn(Opcodes.ILOAD, 1)

    val l2 = new Label()
    val l3 = new Label()
    val l4 = new Label()
    method.visitTableSwitchInsn(1, 3, l4, Array[Label](l2, l3, l4) :_*)

    method.visitLabel(l2)
    method.visitLineNumber(1003, l2)
    method.visitIincInsn(2, 1)

    method.visitLabel(l3)
    method.visitLineNumber(1004, l3)
    method.visitIincInsn(2, 1)

    method.visitLabel(l4)
    method.visitLineNumber(1005, l4)
    method.visitVarInsn(Opcodes.ILOAD, 2)

    method.visitInsn(Opcodes.IRETURN)
    method
  }

  test("Table with merge") {
    val result = analyze(tableSwitchWithMerge)
    assert(result(1002).size == 3 &&
      result(1002).contains(0) && result(1002).contains(1) && result(1002).contains(2))
    assert(result(1005).size == 1 && result(1005).contains(4))
  }

  def tryCatchBlock = {
    val method = emptyMethod
    val l1 = new Label()
    val l2 = new Label()
    val l3 = new Label()
    val l4 = new Label()
    method.visitTryCatchBlock(l1, l2, l3, "java/lang/Exception")

    method.visitLabel(l1)
    method.visitLineNumber(1001, l1)
    method.visitVarInsn(Opcodes.ALOAD, 0)
    method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable",
      "printStackTrace", "()V", false)

    method.visitLabel(l2)
    method.visitJumpInsn(Opcodes.GOTO, l4)

    method.visitLabel(l3)
    method.visitLineNumber(1002, l3)
    method.visitVarInsn(Opcodes.ASTORE, 1)

    method.visitLabel(l4)
    method.visitLineNumber(1004, l4)
    method.visitInsn(Opcodes.RETURN)
    method
  }

  // As far as I can tell, try catch block does not have branches
  test("Try catch block") {
    val result = analyze(tryCatchBlock)
    assert(result(1001).size == 1)
    assert(result(1002).size == 1)
    assert(result(1004).size == 1)
  }
}
