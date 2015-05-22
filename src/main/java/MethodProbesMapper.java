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
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.List;


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

  // Intermediate results
  private final Map<Instruction, CovExp> insnToCovExp = new HashMap();
  private final Map<Instruction, Integer> insnToIdx = new HashMap();

  // Result
  private Map<Integer, BranchExp> lineToBranchExp = new TreeMap();
  public Map<Integer, BranchExp> result() {
    return lineToBranchExp;
  }

  // Intermediate results
  //
  // These values are built up during the visitor methods. They will be used to compute
  // the final results.
  private List<Instruction> instructions = new ArrayList<Instruction>();
  private List<Jump> jumps = new ArrayList<Jump>();
  private Map<Integer, Instruction> probeToInsn = new TreeMap<Integer, Instruction>();
  
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

  // If a CovExp is ProbeExp, create a single-branch BranchExp and put it in the map.
  private BranchExp getBranchExp(Instruction insn, CovExp exp) {
    BranchExp result = null;
    if (exp instanceof ProbeExp) {
      result = exp.branchExp();
      insnToCovExp.put(insn, result);
    } else {
      result = (BranchExp) exp;
    }
    return result;
  }

  // If a CovExp of pred is ProbeExp, create a single-branch BranchExp and put it in the map.
  // Also update the index of insn.
  private BranchExp getPredBranchExp(Instruction predecessor, Instruction insn) {
    BranchExp result = null;
    CovExp exp = insnToCovExp.get(predecessor);
    if (exp instanceof ProbeExp) {
      result = exp.branchExp(); // Change ProbeExp to BranchExp 
      insnToCovExp.put(predecessor, result);
      // This can only happen if an Instruction is the predecessor of more than one
      // instructions but its branch count is not > 0.
      System.err.println("Internal data inconsistent");
    } else {
      result = (BranchExp) exp;
    }
    return result;
  }

  // Update a branch predecessor and returns the BranchExp of the predecessor.
  private BranchExp updateBranchPredecessor(Instruction predecessor, Instruction insn,
    CovExp exp) {
    CovExp predExp = insnToCovExp.get(predecessor);
    if (predExp == null) {
      BranchExp branchExp = exp.branchExp();
      insnToCovExp.put(predecessor, branchExp);
      insnToIdx.put(insn, 0); // current insn is the first branch
      return branchExp;
    } 

    BranchExp branchExp = getPredBranchExp(predecessor, insn);
    Integer branchIdx = insnToIdx.get(insn);
    if (branchIdx == null) {
      branchIdx = branchExp.add(exp);
      insnToIdx.put(insn, branchIdx);
    } else {
      branchExp.update(branchIdx, exp);
    }
    return branchExp;
  }

  /** Finishing the method */
  @Override 
  public void visitEnd() {
    for (Jump jump : jumps) {
      Instruction insn = labelToInsn.get(jump.target);
      insn.setPredecessor(jump.source);
      predecessors.put(insn, jump.source);
    }

    // Compute CovExp for every instruction.
    for (Map.Entry<Integer, Instruction> entry : probeToInsn.entrySet()) {     
      int probeId = entry.getKey();
      Instruction ins = entry.getValue();

      Instruction insn = ins;
      CovExp exp = new ProbeExp(probeId);

      CovExp existingExp = insnToCovExp.get(insn);
      if (existingExp != null) {
        // The instruction already has a branch, add the probeExp as 
        // a new branch.
        BranchExp branchExp = getBranchExp(insn, existingExp);
        branchExp.add(exp); 
      } else {
        insnToCovExp.put(insn, exp);
      }

      Instruction predecessor = predecessors.get(insn);
      while (predecessor != null) {
        if (predecessor.getBranches() > 1) {
          exp = updateBranchPredecessor(predecessor, insn, exp);
        } else {
          // No branch at predecessor, use the same CovExp
          insnToCovExp.put(predecessor, exp);
        }
        insn = predecessor;
        predecessor = predecessors.get(insn);
      }
    }

    // Merge branches in the instructions on the same line
    for (Instruction insn : instructions) {
      if (insn.getBranches() > 1) {
        CovExp insnExp = insnToCovExp.get(insn);
        if (insnExp != null && (insnExp instanceof BranchExp)) {
          BranchExp exp = (BranchExp) insnExp;
          BranchExp lineExp = lineToBranchExp.get(insn.getLine());
          if (lineExp == null) {
            lineToBranchExp.put(insn.getLine(), exp);
          } else {
            lineExp.merge(exp);
          }
        } else {
          System.err.println("Analyzer Internal data inconsistent.");
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
