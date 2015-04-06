package org.jacoco.extra.internal;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;

import org.jacoco.core.internal.flow.MethodProbesVisitor;
import org.jacoco.core.internal.flow.Instruction;
import org.jacoco.core.internal.flow.LabelInfo;
import org.jacoco.core.internal.flow.IFrame;

import com.google.common.collect.Multimap;
import com.google.common.collect.HashMultimap;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

/** 
  * The Jacoco internal relies on the fact that a method visitor would
  * visit the coverage nodes in the same order as they were created to
  * map the information correctly. This is done in the "Adapter"
  * layer, both the ClassProbesAdapter and MethodProbesAdapter are
  * final classes to prevent changing the behavior in subclassing. The
  * actual instrumentation or parsing the runtime data are done in the
  * "Probe visitor" objects, which the main adapter would delegate to.
  *  
  * As the logic to identify probes are all in the adatpers, it
  * ensures that the visitors will always be called in the exact same
  * order. Thus provides repeatability.
  * 
  * In the same manner, if we provide different visitors we are able
  * to map out correspondence between probes and branch instructions. 
  * 
  * The mapper is a probes visitor that will cache control flow
  * information as well as keeping track of the probes as the main
  * driver generates the probe ids. Upon finishing the method it uses
  * the information collected to generate the mapping information
  * between probes and the instructions. 
  * 
  * 
  * Implemenation
  *  
  * The implementation roughly follows the same pattern of the
  * Analyzer. The mapper has a few states:
  *   - lineMappings: a mapping between line number and labels
  * 
  *   - a sequence of "instructions", where each instruction has one
  *     or more predecessors. The predecessor has a sole purpose of
  *     'propagating branch information. Therefore the merge nodes in
  *     the CFG has no predecessors, since the branch information
  *     stops at the merge points.
  * 
  *   - The instructions each has states that keep track of the probes
  *     that are associated with the instruction.
  * 
  * Initially the probes are associated with the instructions that
  * prcedes the probe. At the end of visiting the methods, the probe
  * numbers propagates through the predecessor chains. 
  */

/**
 * A probes mapper is a ProbesVisitor object which is used with ProbesAdapter
 * to traverse the method bytecode to generate mapping between probes and source
 * code. 
 */
public class MethodProbesMapper extends MethodProbesVisitor {
  // States
  //
  // These are state variables that needs to be updated in the visitor methods. 
  // The values usually changes as we traverse the byte code.
  private Instruction lastInstruction = null;
  private int currentLine = -1;
  private List<Label> currentLabels = new ArrayList<Label>();

  // Result
  private Multimap<Integer, Integer> lineToProbes = HashMultimap.<Integer, Integer>create();
  public Multimap<Integer, Integer> result() {
    return lineToProbes;
  }

  // Intermediate results
  //
  // These values are built up during the visitor methods. They will be used to compute
  // the final results.
  private List<Instruction> instructions = new ArrayList<Instruction>();
  private List<Jump> jumps = new ArrayList<Jump>();
  private Map<Integer, Instruction> probeToInsn = new HashMap<Integer, Instruction>();
  
  // Local cache
  //
  // These are maps corresponding to data structures available in JaCoCo in other form.
  // We use local data structure to avoid need to change the JaCoCo internal code.
  private Map<Instruction, Instruction> predecessors = new HashMap<Instruction, Instruction>();
  private Map<Label, Instruction> labelToInsn = new HashMap<Label, Instruction>();
 
  /** Visitor method to append a new Instruction */
  private void visitInsn() {
    Instruction instruction = new Instruction(currentLine);
    instructions.add(instruction);
    if (lastInstruction != null) {
      instruction.setPredecessor(lastInstruction); // Update branch of lastInstruction
      predecessors.put(instruction, lastInstruction); // Update local cache
    }

    int labelCount = currentLabels.size();

    for (Label label: currentLabels) {
      labelToInsn.put(label, instruction);
    }
    currentLabels.clear(); // Update states
    lastInstruction = instruction; 
  }

  // Plain visitors: called from adapter when no probe is needed 
  @Override
  public void visitInsn(int opcode) {
    visitInsn();
  }

  @Override 
  public void visitIntInsn(int opcode, int operand) {
    visitInsn();
  }

  @Override
  public void visitVarInsn(int opcode, int variable) {
    visitInsn();
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    visitInsn();
  }

  @Override
  public void visitFieldInsn(int opcode, String owner, String name, String desc) {
    visitInsn();
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, 
    String desc, boolean itf) {
    visitInsn();
  }

  @Override
  public void visitInvokeDynamicInsn(String name, String desc, Handle handle,
    Object... args) {
    visitInsn();
  }

  @Override
  public void visitLdcInsn(Object cst) {
    visitInsn();
  }

  @Override
  public void visitIincInsn(int var, int inc) {
    visitInsn();
  }

  @Override
  public void visitMultiANewArrayInsn(String desc, int dims) {
    visitInsn();
  }


  // Methods that need to update the states
  @Override
  public void visitJumpInsn(int opcode, Label label) {
    visitInsn();
    jumps.add(new Jump(lastInstruction, label));
  }
  
  @Override
  public void visitLabel(Label label) {
    currentLabels.add(label);
    if (!LabelInfo.isSuccessor(label)) {
      lastInstruction = null;
    }
  }

  @Override
  public void visitLineNumber(int line, Label start) {
    currentLine = line;
  }

  /** Visit a switch instruction with no probes */
  private void visitSwitchInsn(Label dflt, Label[] labels) {
    visitInsn();

    // Handle default transition
    LabelInfo.resetDone(dflt);
    jumps.add(new Jump(lastInstruction, dflt));
    LabelInfo.setDone(dflt);

    // Handle other transitions
    LabelInfo.resetDone(labels);
    for (Label label: labels) {
      if (!LabelInfo.isDone(label)) {
        jumps.add(new Jump(lastInstruction, label));
        LabelInfo.setDone(label);
      }
    }    
  }

  @Override
  public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) { 
    visitSwitchInsn(dflt, labels);
  }

  @Override
  public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
    visitSwitchInsn(dflt, labels);
  }

  private void addProbe(int probeId) {
    // We do not add probes to the flow graph, but we need to update
    // the branch count of the predecessor of the probe
    lastInstruction.addBranch();
    probeToInsn.put(probeId, lastInstruction);
  }

  // Probe visit methods
  @Override
  public void visitProbe(int probeId) {
    // This function is only called when visiting a merge node which
    // is a successor.
    // It adds an probe point to the last instruction
    assert(lastInstruction != null);

    addProbe(probeId);
    lastInstruction = null; // Merge point should have no predecessor.    
  }

  @Override 
  public void visitJumpInsnWithProbe(int opcode, Label label, int probeId, IFrame frame) {
    visitInsn();
    addProbe(probeId);
  }

  @Override
  public void visitInsnWithProbe(int opcode, int probeId) {
    visitInsn();
    addProbe(probeId);
  }

  @Override 
  public void visitTableSwitchInsnWithProbes(int min, int max,
    Label dflt, Label[] labels, IFrame frame) {
    visitSwitchInsnWithProbes(dflt, labels);
  }

  @Override 
  public void visitLookupSwitchInsnWithProbes(Label dflt,
    int[] keys, Label[] labels, IFrame frame) {
    visitSwitchInsnWithProbes(dflt, labels);
  }

  private void visitSwitchInsnWithProbes(Label dflt, Label[] labels) {
    visitInsn();
    LabelInfo.resetDone(dflt);
    LabelInfo.resetDone(labels);

    visitTargetWithProbe(dflt);
    for (Label l : labels) {
      visitTargetWithProbe(l);
    }
  }

  private void visitTargetWithProbe(Label label) {
    if (!LabelInfo.isDone(label)) {
      int id = LabelInfo.getProbeId(label);
      if (id == LabelInfo.NO_PROBE) {
        jumps.add(new Jump(lastInstruction, label));
      } else {
        // Note, in this case the instrumenter should insert intermediate labels
        // for the probes. These probes will be added for the switch instruction.
        // 
        // There is no direct jump between lastInstruction and the label either.
        addProbe(id); 
      }
      LabelInfo.setDone(label);
    }
  }  

  /** Finishing the method */
  @Override 
  public void visitEnd() {
    for (Jump jump : jumps) {
      Instruction insn = labelToInsn.get(jump.target);
      insn.setPredecessor(jump.source);
      predecessors.put(insn, jump.source);
    }

    for (Map.Entry<Integer, Instruction> entry : probeToInsn.entrySet()) {     
      int probeId = entry.getKey();
      Instruction i = entry.getValue();

      Instruction insn = i;
      while (insn != null) {
        lineToProbes.put(insn.getLine(), probeId); 
        if (insn.getBranches() > 1) {
          insn = null; // break at branches 
        } else {
          insn = predecessors.get(insn);
        }
      }
    }
  }

  /**
    * Jumps between instructions and labels
    */
  class Jump { 
    public final Instruction source;
    public final Label target;

    public Jump(Instruction i, Label l) {
      source = i;
      target = l;
    }
  }
} 
