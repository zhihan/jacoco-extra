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
    mapper
  }

  

}
