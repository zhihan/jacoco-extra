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

  private void createLinearSequence() {
    method.visitLineNumber(1001, new Label());
    method.visitInsn(Opcodes.NOP);
    method.visitLineNumber(1002, new Label());
    method.visitInsn(Opcodes.RETURN);
  }

  private Multimap<Integer, Integer> analyze() {
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
}
