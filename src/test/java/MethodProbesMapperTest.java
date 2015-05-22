package org.jacoco.extra.internal;

import static com.google.common.truth.Truth.assertThat;

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

import java.util.ArrayList;
import java.util.Map;

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

  public Map<Integer, BranchExp> analyze() {
    MethodProbesMapper mapper = new MethodProbesMapper();
    MethodProbesAdapter methodAdapter = new MethodProbesAdapter(mapper, this);
    LabelFlowAnalyzer.markLabels(method);
    method.accept(methodAdapter);
    return mapper.result();
  }

  @Test
  public void testLinearSequence() {
    createLinearSequence();
    Map<Integer, BranchExp> result = analyze();

    assertThat(nextProbeId).isEqualTo(1);
    assertThat(result).isEmpty();
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
    Map<Integer, BranchExp> result = analyze();

    assertThat(nextProbeId).isEqualTo(2);
    assertThat(result).containsKey(1001);
    assertThat(result.get(1001).getBranches()).hasSize(2);
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
    Map<Integer, BranchExp> result = analyze();

    assertThat(nextProbeId).isEqualTo(3);
    assertThat(result).hasSize(1);
    assertThat(result).containsKey(1001);
    assertThat(result.get(1001).getBranches()).hasSize(2);
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
    Map<Integer, BranchExp> result = analyze();

    assertThat(nextProbeId).isEqualTo(1);
    assertThat(result).isEmpty();
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
    Map<Integer, BranchExp> result = analyze();
    assertThat(nextProbeId).isEqualTo(2);
    assertThat(result).hasSize(1);
    assertThat(result).containsKey(1001);
    assertThat(result.get(1001).getBranches()).hasSize(2);
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
    Map<Integer, BranchExp> result = analyze();

    assertThat(nextProbeId).isEqualTo(4);
    assertThat(result).hasSize(1);
    assertThat(result).containsKey(1001);
    assertThat(result.get(1001).getBranches()).hasSize(3);
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
    Map<Integer, BranchExp> result = analyze();

    assertThat(nextProbeId).isEqualTo(5);
    assertThat(result).hasSize(1);
    assertThat(result).containsKey(1002);
    assertThat(result.get(1002).getBranches()).hasSize(3);
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
    Map<Integer, BranchExp> result = analyze();

    assertThat(nextProbeId).isEqualTo(3);
    assertThat(result).isEmpty();
  }

  public void createNullCheckMethod() {
    method.visitLineNumber(5, new Label());
    method.visitVarInsn(Opcodes.ASTORE, 2);

    Label line6 = new Label();
    method.visitLineNumber(6, line6);
    method.visitVarInsn(Opcodes.ALOAD, 1);
    Label l1 = new Label();
    method.visitJumpInsn(Opcodes.IFNONNULL, l1);

    Label line7 = new Label();
    method.visitLineNumber(7, line7);
    method.visitInsn(Opcodes.ACONST_NULL);
    method.visitInsn(Opcodes.ARETURN);

    method.visitLabel(l1);
    method.visitLineNumber(10, l1);
    method.visitVarInsn(Opcodes.ALOAD, 1);
    method.visitLdcInsn("yes");
    method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
        "java/lang/String", "equals",
        "(Ljava/lang/Object;)Z", false);
    Label l2 = new Label();
    method.visitJumpInsn(Opcodes.IFEQ, l2);

    Label line11 = new Label();
    method.visitLineNumber(11, line11);
    method.visitLdcInsn("yes");
    method.visitInsn(Opcodes.ARETURN);

    method.visitLabel(l2);
    method.visitLineNumber(13, l2);
    method.visitLdcInsn("no");
    method.visitInsn(Opcodes.ARETURN);
  }

  @Test
  public void testNullCheckMethod() {
    createNullCheckMethod();
    Map<Integer, BranchExp> result = analyze();

    assertThat(nextProbeId).isEqualTo(3);
    assertThat(result).hasSize(2);
    assertThat(result).containsKey(6);
    assertThat(result.get(6).getBranches()).hasSize(2);
    assertThat(result).containsKey(10);
    assertThat(result.get(10).getBranches()).hasSize(2);
  }

  public void createExprMethod() {
    method.visitLineNumber(1, new Label());
    method.visitVarInsn(Opcodes.ILOAD, 1);
    Label l1 = new Label();
    method.visitJumpInsn(Opcodes.IFLE, l1);
    method.visitInsn(Opcodes.ICONST_1);

    Label l2 = new Label();
    method.visitJumpInsn(Opcodes.GOTO, l2);

    method.visitLabel(l1);
    method.visitInsn(Opcodes.ICONST_0);

    method.visitLabel(l2);
    method.visitMethodInsn(
      Opcodes.INVOKESTATIC,
      "com/google/common/base/Preconditions",
      "checkArgument",
      "(Z)V",
      false);
    method.visitLineNumber(2, new Label());
    method.visitVarInsn(Opcodes.ILOAD, 1);
    method.visitInsn(Opcodes.IRETURN);
  }

  @Test
  public void testExprMethod() {
    createExprMethod();
    Map<Integer, BranchExp> result = analyze();

    assertThat(nextProbeId).isEqualTo(3);
    assertThat(result).hasSize(1);
    assertThat(result).containsKey(1);
    assertThat(result.get(1).getBranches()).hasSize(2);
  }
}
