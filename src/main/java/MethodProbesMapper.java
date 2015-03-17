package org.jacoco.extra.internal;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;

import org.jacoco.core.internal.flow.MethodProbesVisitor;
import org.jacoco.core.internal.flow.Instruction;
import org.jacoco.core.internal.flow.LabelInfo;

import com.google.common.collect.Multimap;
import com.google.common.collect.HashMultimap;

import java.util.Map;
import java.util.HashMap;
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

  // Result
  private Multimap<Integer, Integer> lineToProbes = HashMultimap.<Integer, Integer>create();

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
 
  // Visitor method to append a new Instruction
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

  /**
    * Jumps between instructions and labels
    */
  class Jump{ 
    public Instruction source;
    public Label target;

    public Jump(Instruction i, Label l) {
      source = i;
      target = l;
    }
  }
} 
