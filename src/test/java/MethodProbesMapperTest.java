package org.jacoco.extra.internal;

import org.junit.Test;
import org.junit.Before;
import org.junit.Assert;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import org.jacoco.core.internal.flow.MethodProbesAdapter;
import org.jacoco.core.internal.flow.LabelFlowAnalyzer;
import org.jacoco.core.internal.flow.IProbeIdGenerator;

import com.google.common.collect.Multimap;
import java.util.ArrayList;

public class MethodProbesMapperTest implements IProbeIdGenerator {
  private int nextProbeId;
  private MethodNode method;

  @Before
  public void setup() {
    nextProbeId = 0;
    method = new MethodNode();
    method.tryCatchBlocks = new ArrayList<TryCatchBlockNode>();
  }

  public int nextId() {
    return nextProbeId++;
  }

  // Simple linear sequence of instructions.
  private void createLinearSequence() {
    method.visitLineNumber(1001, new Label());
    method.visitInsn(Opcodes.NOP);
    method.visitLineNumber(1002, new Label());
    method.visitInsn(Opcodes.RETURN);
  }

  public Multimap<Integer, Integer> analyze() {
    MethodProbesMapper mapper = new MethodProbesMapper();
    MethodProbesAdapter methodAdapter = new MethodProbesAdapter(mapper, this);
    LabelFlowAnalyzer.markLabels(method);
    method.accept(methodAdapter);
    return mapper.result();
  }

  @Test
  public void testLinearSequence() {
    createLinearSequence();
    Multimap<Integer, Integer> result = analyze();
    
    Assert.assertEquals(1, nextProbeId);
    Assert.assertTrue(result.containsEntry(1001, 0));
    Assert.assertTrue(result.containsEntry(1002, 0));
    Assert.assertEquals(result.size(), 2);
  }

  private void createIfBranch() {
    method.visitLineNumber(1001, new Label());
    method.visitVarInsn(Opcodes.ILOAD, 1);
    Label l1 = new Label();
    method.visitJumpInsn(Opcodes.IFEQ, l1);
    method.visitLineNumber(1002, new Label());
    method.visitLdcInsn("a");
    method.visitInsn(Opcodes.ARETURN);
    method.visitLabel(l1);
    method.visitLineNumber(1003, l1);
    method.visitLdcInsn("b");
    method.visitInsn(Opcodes.ARETURN);
  }

  @Test
  public void testIfBranch() {
    createIfBranch();
    Multimap<Integer, Integer> result = analyze();

    Assert.assertEquals(2, nextProbeId);
    Assert.assertEquals(result.get(1002).size(), 1);
    Assert.assertEquals(result.get(1003).size(), 1);
  }

  private void createIfBranchMerge() {
    method.visitLineNumber(1001, new Label());
    method.visitVarInsn(Opcodes.ILOAD, 1);
    Label l1 = new Label();
    method.visitJumpInsn(Opcodes.IFEQ, l1);
    method.visitLineNumber(1002, new Label());
    method.visitInsn(Opcodes.NOP);
    method.visitLabel(l1);
    method.visitLineNumber(1003, l1);
    method.visitInsn(Opcodes.RETURN);
  }

  @Test
  public void testIfBranchMerge() {
    createIfBranchMerge();
    Multimap<Integer, Integer> result = analyze();

    Assert.assertEquals(3, nextProbeId);
    Assert.assertEquals(2, result.get(1001).size());
    Assert.assertEquals(1, result.get(1002).size());
    Assert.assertEquals(1, result.get(1003).size());
    Assert.assertTrue(result.containsEntry(1003, 2));
    Assert.assertTrue(result.containsEntry(1001, 0));
    Assert.assertTrue(result.containsEntry(1001, 1));
  }

  private void createJumpBackwards() {
    method.visitLineNumber(1001, new Label());
    final Label l1 = new Label();
    method.visitJumpInsn(Opcodes.GOTO, l1);
    final Label l2 = new Label();
    method.visitLabel(l2);
    method.visitLineNumber(1002, l2);
    method.visitInsn(Opcodes.RETURN);
    method.visitLabel(l1);
    method.visitLineNumber(1003, l1);
    method.visitJumpInsn(Opcodes.GOTO, l2);
  }

  @Test
  public void testJumpBackwards() {
    createJumpBackwards();
    Multimap<Integer, Integer> result = analyze();

    Assert.assertEquals(1, nextProbeId);
    Assert.assertEquals(1, result.get(1001).size());
    Assert.assertEquals(1, result.get(1002).size());
    Assert.assertEquals(1, result.get(1003).size());
    Assert.assertTrue(result.containsEntry(1001, 0));
    Assert.assertTrue(result.containsEntry(1002, 0));
    Assert.assertTrue(result.containsEntry(1003, 0));
  }

  private void createJumpToFirst() {
    final Label l1 = new Label();
    method.visitLabel(l1);
    method.visitLineNumber(1001, l1);
    method.visitVarInsn(Opcodes.ALOAD, 0);
    method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Foo", "test", "()Z",
        false);
    method.visitJumpInsn(Opcodes.IFEQ, l1);
    final Label l2 = new Label();
    method.visitLabel(l2);
    method.visitLineNumber(1002, l2);
    method.visitInsn(Opcodes.RETURN);
  }

  @Test
  public void testJumpToFirst() {
    createJumpToFirst();
    Multimap<Integer, Integer> result = analyze();

    Assert.assertEquals(2, nextProbeId);
    Assert.assertEquals(2, result.get(1001).size());
    Assert.assertEquals(1, result.get(1002).size());
    Assert.assertTrue(result.containsEntry(1001, 0));
    Assert.assertTrue(result.containsEntry(1001, 1));
  }

  public void createTableSwitch() {
    method.visitLineNumber(1001, new Label());
    method.visitVarInsn(Opcodes.ILOAD, 1);
    Label l1 = new Label();
    Label l2 = new Label();
    Label l3 = new Label();
    method.visitTableSwitchInsn(1, 3, l3, new Label[] { l1, l2, l1 });
    
    method.visitLabel(l1);
    method.visitLineNumber(1002, l1);
    method.visitIntInsn(Opcodes.BIPUSH, 11);
    method.visitVarInsn(Opcodes.ISTORE, 2);
    method.visitLineNumber(1003, new Label());    
    Label l5 = new Label();
    method.visitJumpInsn(Opcodes.GOTO, l5);
    
    method.visitLabel(l2);
    method.visitLineNumber(1004, l2);
    method.visitIntInsn(Opcodes.BIPUSH, 22);
    method.visitVarInsn(Opcodes.ISTORE, 2);
    method.visitLineNumber(1005, new Label());
    method.visitJumpInsn(Opcodes.GOTO, l5);

    method.visitLabel(l3);
    method.visitLineNumber(1006, l3);
    method.visitInsn(Opcodes.ICONST_0);
    method.visitVarInsn(Opcodes.ISTORE, 2);
    
    method.visitLabel(l5);
    method.visitLineNumber(1007, l5);
    method.visitVarInsn(Opcodes.ILOAD, 2);
    method.visitInsn(Opcodes.IRETURN);
  }

  @Test
  public void testTableSwitch() {
    createTableSwitch();
    Multimap<Integer, Integer> result = analyze();

    Assert.assertEquals(4, nextProbeId);
    Assert.assertEquals(3, result.get(1001).size());
    Assert.assertTrue(result.containsEntry(1001, 0));
    Assert.assertTrue(result.containsEntry(1001, 1));
    Assert.assertTrue(result.containsEntry(1001, 2));
    Assert.assertTrue(result.containsEntry(1007, 3));
  }

  private void createTableSwitchMerge() {
    method.visitLineNumber(1001, new Label());
    method.visitInsn(Opcodes.ICONST_0);
    method.visitVarInsn(Opcodes.ISTORE, 2);
    method.visitLineNumber(1002, new Label());
    method.visitVarInsn(Opcodes.ILOAD, 1);
    Label l2 = new Label();
    Label l3 = new Label();
    Label l4 = new Label();
    method.visitTableSwitchInsn(1, 3, l4, new Label[] { l2, l3, l2 });
    method.visitLabel(l2);
    method.visitLineNumber(1003, l2);
    method.visitIincInsn(2, 1);
    method.visitLabel(l3);
    method.visitLineNumber(1004, l3);
    method.visitIincInsn(2, 1);
    method.visitLabel(l4);
    method.visitLineNumber(1005, l4);
    method.visitVarInsn(Opcodes.ILOAD, 2);
    method.visitInsn(Opcodes.IRETURN);
  }

  @Test
  public void testTableSwitchMerge() {
    createTableSwitchMerge();
    Multimap<Integer, Integer> result = analyze();

    Assert.assertEquals(5, nextProbeId);
    Assert.assertEquals(3, result.get(1002).size());
    Assert.assertTrue(result.containsEntry(1002, 0));
    Assert.assertTrue(result.containsEntry(1002, 1));
    Assert.assertTrue(result.containsEntry(1002, 2));
    Assert.assertTrue(result.containsEntry(1005, 4));
  }

  private void createTryCatchBlock() {
    Label l1 = new Label();
    Label l2 = new Label();
    Label l3 = new Label();
    Label l4 = new Label();
    method.visitTryCatchBlock(l1, l2, l3, "java/lang/Exception");
    method.visitLabel(l1);
    method.visitLineNumber(1001, l1);
    method.visitVarInsn(Opcodes.ALOAD, 0);
    method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable",
      "printStackTrace", "()V", false);
    method.visitLabel(l2);
    method.visitJumpInsn(Opcodes.GOTO, l4);
    method.visitLabel(l3);
    method.visitLineNumber(1002, l3);
    method.visitVarInsn(Opcodes.ASTORE, 1);
    method.visitLabel(l4);
    method.visitLineNumber(1004, l4);
    method.visitInsn(Opcodes.RETURN);
  }

  @Test
  public void testTryCatchBlock() {
    createTryCatchBlock();
    Multimap<Integer, Integer> result = analyze();

    Assert.assertEquals(3, nextProbeId);
    Assert.assertEquals(1, result.get(1001).size());
    Assert.assertEquals(1, result.get(1002).size());
    Assert.assertEquals(1, result.get(1004).size());

  }
}
