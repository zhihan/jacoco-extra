package me.zhihan.jacoco.internal

import org.scalatest.FunSuite
import org.objectweb.asm.{Opcodes, Label, ClassReader}
import org.objectweb.asm.tree.{MethodNode, TryCatchBlockNode}
import org.jacoco.core.internal.flow.{MethodProbesAdapter, LabelFlowAnalyzer}

import scala.collection.mutable.{Map, ArrayBuffer}
import java.io.InputStream

class MethodMapperTest extends FunSuite {
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
    mapper.lineToBranchExp
  }

  def debug(method: MethodNode) = {
    val mapper = new MethodProbesMapper()
    val methodAdapter = new MethodProbesAdapter(mapper, new MyIdGenerator())
    LabelFlowAnalyzer.markLabels(method)
    method.accept(methodAdapter)
    mapper
  }

  test("Linear Sequence with return map") {
    val result = analyze(linearSeqMethod)
    assert(!result.contains(1001) && !result.contains(1002))
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
    assert(result(1001).branches.size == 2 && 
      result(1001).branches.contains(ProbeExp(0)) && 
      result(1001).branches.contains(ProbeExp(1)))
    assert(!result.contains(1002))
    assert(!result.contains(1003))
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
    assert(result(1001).branches.size == 2 && 
      result(1001).branches.contains(ProbeExp(0)) && 
      result(1001).branches.contains(ProbeExp(1)))
    assert(!result.contains(1002))
    assert(!result.contains(1003))
  }

  
  def jumpBackwardMethod = {
    // No branches at all
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
    assert(result.size == 0)
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
    assert(result(1001).branches.size == 2 &&
      result(1001).branches.contains(ProbeExp(0)) && 
      result(1001).branches.contains(ProbeExp(1)))
    assert(!result.contains(1002))
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
    assert(result(1001).branches.size == 3 &&
      result(1001).branches.contains(ProbeExp(0)) && 
      result(1001).branches.contains(ProbeExp(1)) && 
      result(1001).branches.contains(ProbeExp(2)))
    assert(!result.contains(1007) && 
      !result.contains(1007))
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
    assert(result(1002).branches.size == 3 &&
      result(1002).branches.contains(ProbeExp(0)) && 
      result(1002).branches.contains(ProbeExp(1)) && 
      result(1002).branches.contains(ProbeExp(2)))
    assert(!result.contains(1005))
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
    assert(result.isEmpty)
  }

  // An actual method that checks if input is null and return early.
  def nullTestMethod = {
    val method = emptyMethod
    method.visitVarInsn(Opcodes.ASTORE, 2)
    val line6 = new Label()
    method.visitLineNumber(6, line6)
    method.visitVarInsn(Opcodes.ALOAD, 1)
    val l1 = new Label()
    method.visitJumpInsn(Opcodes.IFNONNULL, l1)

    val line7 = new Label()
    method.visitLineNumber(7, line7)
    method.visitInsn(Opcodes.ACONST_NULL)
    method.visitInsn(Opcodes.ARETURN)

    method.visitLabel(l1)
    method.visitLineNumber(10, l1)
    method.visitVarInsn(Opcodes.ALOAD, 1)
    method.visitLdcInsn("yes")
    method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
      "java/lang/String", "equals",
      "(Ljava/lang/Object;)Z", false)
    val l2 = new Label()
    method.visitJumpInsn(Opcodes.IFEQ, l2)

    val line11 = new Label()
    method.visitLineNumber(11, line11)
    method.visitLdcInsn("yes")
    method.visitInsn(Opcodes.ARETURN)

    method.visitLabel(l2)
    method.visitLineNumber(13, l2)
    method.visitLdcInsn("no")
    method.visitInsn(Opcodes.ARETURN)
    method
  }

  test("Method with null check early return") {
    val result = analyze(nullTestMethod)
    assert(result(6).branches.contains(ProbeExp(0)) &&
      result(6).branches.contains(BranchExp(ArrayBuffer(ProbeExp(2), ProbeExp(1)))))
    assert(result(10).branches.contains(ProbeExp(1)) &&
      result(10).branches.contains(ProbeExp(2)))
  }

  // An boolean expression is expanded to if-else branch.
  def exprMethod = {
    val method = emptyMethod
    method.visitLineNumber(1, new Label())
    method.visitVarInsn(Opcodes.ILOAD, 1)
    val l1 = new Label()
    method.visitJumpInsn(Opcodes.IFLE, l1)

    method.visitVarInsn(Opcodes.ILOAD, 2)
    method.visitJumpInsn(Opcodes.IFLE, l1)
    method.visitInsn(Opcodes.ICONST_1)

    val l2 = new Label()
    method.visitJumpInsn(Opcodes.GOTO, l2)
    method.visitLabel(l1)
    method.visitInsn(Opcodes.ICONST_0)

    method.visitLabel(l2)
    method.visitMethodInsn(
      Opcodes.INVOKESTATIC,
      "com/google/common/base/Preconditions",
      "checkArgument",
      "(Z)V",
      false)
    method.visitLineNumber(2, new Label())
    method.visitVarInsn(Opcodes.ILOAD, 1)
    method.visitInsn(Opcodes.IRETURN)
    method
  }

  test("Expand logical expression") {
    val result = analyze(exprMethod)
    assert(result(1).branches.size == 4)
    println(result(1).branches)
    assert(result(1).branches.contains(ProbeExp(0)))
    assert(result(1).branches.contains(ProbeExp(1)))
    assert(result(1).branches.contains(ProbeExp(2)))
  }
}


class ClassMapperTest extends FunSuite {
  def newAnalyzer = {
    val mapper = new ClassProbesMapper()
    mapper.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC, "Foo", null,
      "java/lang/Object", null)
    mapper
  }


  test("Non empty method") {
    val mapper = newAnalyzer

    val mv = mapper.visitMethod(0, "foo", "()V", null, null)
    mv.visitCode()
    mv.visitInsn(Opcodes.RETURN)
    mv.visitEnd()
    
    assert(mapper.classLineToBranchExp.isEmpty)
  }
}

class MapperTest extends FunSuite {
  class MemoryClassLoader(cl: ClassLoader) extends ClassLoader(cl) {
    val definitions = Map[String, Array[Byte]]()

    /**
      * Add a in-memory representation of a class.
      * 
      * @param name
      *            name of the class
      * @param bytes
      *            class definition
      */
    def addDefinition(name:String, bytes:Array[Byte]) {
      definitions(name) = bytes
    }

    override protected def loadClass(name:String, resolve:Boolean) = {
      val bytes = definitions.get(name);
      bytes match {
        case Some(b) => defineClass(name, b, 0, b.length)
        case None => super.loadClass(name, resolve)
      }
    }    
  }

  def getTargetClass(name:String): InputStream = {
    val resource = "/" + name.replace(".", "/") + ".class"
    getClass().getResourceAsStream(resource)
  }

  test("Domonstrate mapping function") {
    val mapper = new Mapper()
    val c = new MyC()
    val inputS = getTargetClass("me.zhihan.jacoco.internal.MyC")
    val m = mapper.analyzeClass(new ClassReader(inputS))
    assert(m.contains(11)) // Make sure the source code does not change
    assert(m(11).branches.size == 2)
  }
} 
