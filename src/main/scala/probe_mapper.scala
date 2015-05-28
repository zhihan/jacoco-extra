package me.zhihan.jacoco.internal

import org.jacoco.core.internal.flow.{MethodProbesVisitor, IFrame, 
  IProbeIdGenerator, Instruction, LabelInfo, ClassProbesVisitor, 
  ClassProbesAdapter}
import org.objectweb.asm.{Handle, Label, FieldVisitor, ClassReader}
import scala.collection.mutable.{Map, Set, ArrayBuffer}

/**
  A Simple class that implements the id generator interface.
  */
class MyIdGenerator extends IProbeIdGenerator {
  private var id = -1

  override def nextId = {
    id += 1
    id
  }
}

case class Jump(val source: Instruction, val target:Label) {}

// Assuming LabelInfo is available!
/** A method probes mapper is a probes visitor that visits the probes and 
  keeps a map between probes and lines */
class MethodProbesMapper extends MethodProbesVisitor {
  var lastInstruction: Instruction = null
  var currentLine: Int = -1

  // Probes to the predecessors of the probes
  // The chain of predecessors stops at probe points.
  val probeToInsn: Map[Int, Instruction] = Map()

  // A local cache of predecessors as this info is not exposed in Jacoco.
  val pred: Map[Instruction, Instruction] = Map() 

  // Map instruction to the branch index of in the predecessor
  val insnToIdx: Map[Instruction, Int] = Map()
  // Map instruction to the 
  val insnToCovExp: Map[Instruction, CovExp] = Map()
  // Final results
  val lineToBranchExp: Map[Int, BranchExp] = Map()

  val instructions: ArrayBuffer[Instruction] = ArrayBuffer()
  val jumps: ArrayBuffer[Jump] = ArrayBuffer()
  val currentLabels: ArrayBuffer[Label] = ArrayBuffer()
  val labelToInstruction: Map[Label, Instruction] = Map()

  /** Add a new instruction to the end of the */
  private def visitInstruction {
    val instruction = new Instruction(currentLine)
    instructions.append(instruction)
    if (lastInstruction != null) {
      instruction.setPredecessor(lastInstruction)
      pred += instruction -> lastInstruction
    }

    currentLabels.foreach{ label =>
      labelToInstruction += label -> instruction
    }
    currentLabels.clear;
    lastInstruction = instruction
  }

  // Plain instructions without any probes, delegate to the private method
  override def visitInsn(opcode: Int) = visitInstruction
  override def visitIntInsn(opcode: Int, operand: Int) = visitInstruction
  override def visitVarInsn(opcode: Int, variable: Int) = visitInstruction
  override def visitTypeInsn(opcode: Int, ty: String) = visitInstruction
  override def visitFieldInsn(opcode: Int, owner: String, 
    name: String, desc:String) = visitInstruction
  override def visitMethodInsn(opcode: Int, owner: String, name: String, 
    desc: String, itf:Boolean) = visitInstruction
  override def visitInvokeDynamicInsn(name: String, desc: String, handle: Handle,
    args: Object*) = visitInstruction
  override def visitLdcInsn(cst: Any) = visitInstruction
  override def visitIincInsn(v:Int, inc: Int) = visitInstruction
  override def visitMultiANewArrayInsn(desc: String, dims: Int) = visitInstruction
  
  override def visitJumpInsn(opcode: Int, label: Label) {
    visitInstruction
    jumps.append(new Jump(lastInstruction, label))
  }

  override def visitLabel(label: Label) {
    currentLabels.append(label)
    if (!LabelInfo.isSuccessor(label)) {
      lastInstruction = null;
    }
  }

  override def visitTableSwitchInsn(min: Int, max: Int, dflt: Label, labels:Label*) = 
    visitSwitchInsn(dflt, labels.toArray)

  override def visitLookupSwitchInsn(dflt: Label, keys: Array[Int], 
    labels: Array[Label]) =
    visitSwitchInsn(dflt, labels)

  def visitSwitchInsn(dflt: Label, labels: Array[Label]) {
    visitInstruction
    LabelInfo.resetDone(dflt)
    jumps.append(new Jump(lastInstruction, dflt))
    LabelInfo.setDone(dflt)

    LabelInfo.resetDone(labels)
    labels.foreach { label =>
      if (!LabelInfo.isDone(label)) {
        jumps.append(new Jump(lastInstruction, label))
        LabelInfo.setDone(label)
      }
    }
  }

  def addProbe(probeId: Int) {
    // We do not add probes to the flow graph, but we need to update
    // the branch count of the predecessor of the probe
    lastInstruction.addBranch
    probeToInsn += probeId -> lastInstruction
  }

  override def visitProbe(probeId: Int) {
    // This function is only called when visiting a merge node which
    // is a successor.
    // It adds an probe point to the last instruction
    assert(lastInstruction != null)
    addProbe(probeId)
    lastInstruction = null // Merge point should have no predecessor.
  }

  override def visitJumpInsnWithProbe(opcode: Int, label:Label,
    probeId: Int, frame:IFrame) {
    visitInstruction  // This is not a typo
    addProbe(probeId)
  }

  override def visitInsnWithProbe(opcode: Int, probeId: Int) {
    visitInstruction
    addProbe(probeId)
  }

  override def visitTableSwitchInsnWithProbes(min: Int, max:Int,
    dflt:Label, labels: Array[Label], frame: IFrame) {
    visitSwitchInsnWithProbes(dflt, labels)
  }

  override def visitLookupSwitchInsnWithProbes(dflt: Label,
    keys: Array[Int], labels: Array[Label], frame: IFrame) {
    visitSwitchInsnWithProbes(dflt, labels)
  }

  def visitSwitchInsnWithProbes(dflt: Label, labels: Array[Label]) {
    visitInstruction
    LabelInfo.resetDone(dflt)
    LabelInfo.resetDone(labels)

    visitTargetWithProbe(dflt)
    labels.foreach{ l => visitTargetWithProbe(l) }
  }

  def visitTargetWithProbe(label: Label) = 
    if (!LabelInfo.isDone(label)) {
      val id = LabelInfo.getProbeId(label)
      if (id == LabelInfo.NO_PROBE) {
        jumps.append(new Jump(lastInstruction, label))
      } else {
        // Note, in this case the instrumenter should insert intermediate labels
        // for the probes. These probes will be added for the switch instruction.
        // 
        // There is no direct jump between lastInstruction and the label either.
        addProbe(id) 
      }
      LabelInfo.setDone(label)
    }

  override def visitLineNumber(line: Int, start: Label) { currentLine = line }

  /** Finishing the method */
  override def visitEnd {
    jumps.foreach{ jump =>
      {
        val insn = labelToInstruction(jump.target)
        insn.setPredecessor(jump.source)
        pred += insn -> jump.source        
      }
    }

    // Updaet predecessor and returns its branchExp
    def updatePredecessor(predecessor: Instruction, insn:Instruction, 
      exp: CovExp) : (Boolean, BranchExp) = { 
      if (!insnToCovExp.contains(predecessor)) {
        val branchExp = exp.branchExp
        insnToCovExp += predecessor -> branchExp
        insnToIdx += (insn -> 0)
        (true, branchExp)
      } else {
        val branchExp = insnToCovExp(predecessor) match {
          case p:ProbeExp => {
            val b = p.branchExp
            insnToCovExp(predecessor) = b
            println("Warning: first branch unknown")
            // Don't know which one is the first branch, this is because
            // Jacoco's branch count does not think the node has branches.
            b
          }
          case b: BranchExp => b
        }
        if (!insnToIdx.contains(insn)) {
          val idx = branchExp.append(exp)
          insnToIdx += (insn -> idx)
        } // Otherwise no need to update because shared mutable objects.
        (false, branchExp)
      }
    }

    probeToInsn.foreach { case(probeId, instruction) => 
      var insn = instruction
      var exp: CovExp = new ProbeExp(probeId)

      if (insnToCovExp.contains(insn)) {
        insnToCovExp(insn).asInstanceOf[BranchExp].append(exp)
      } else {
        if (insn.getBranches > 1) {
          exp = exp.branchExp
        }
        insnToCovExp += (insn -> exp)
      }

      while (insn != null && pred.contains(insn)) {
        val predecessor = pred(insn)
        if (predecessor.getBranches > 1) {
          val (isNew, predExp) = updatePredecessor(predecessor, insn, exp)
          exp = predExp
        } else {
          insnToCovExp += (predecessor -> exp)
        }
        insn = predecessor
      }
    }

    instructions.foreach { insn =>
      if (insn.getBranches > 1) {
        // Add to the line branches
        val insnExp = insnToCovExp(insn).asInstanceOf[BranchExp]
        var probes = lineToBranchExp.getOrElse(insn.getLine, null)
        if (probes == null) {
          lineToBranchExp(insn.getLine) = insnExp
        } else {
          probes.join(insnExp)
        }
      }
    }
  }
}

/** Class probes mapper that computes a map from lines to probe ids.*/
class ClassProbesMapper extends ClassProbesVisitor {
  val classLineToBranchExp:Map[Int, BranchExp] = Map()

  /** Create a method probes mapper and analyze a method */
  override def visitMethod(access: Int, name: String,
    desc: String, signature: String, 
    exceptions: Array[String]): MethodProbesVisitor =
    new MethodProbesMapper {
      override def visitEnd {
        super.visitEnd
        classLineToBranchExp ++= lineToBranchExp
      }
    }
  
  override def visitField(access: Int, name: String,
    desc: String, signature: String, value: Any): FieldVisitor = 
    super.visitField(access, name, desc, signature, value)
  
  override def visitTotalProbeCount(count: Int) {
    // Maybe do some sanity checks.
    // println(s"Total ${count} probes inserted.")
  }
}

/** The main mapper class */
class Mapper {
  def analyzeClass(reader: ClassReader): Map[Int, BranchExp] = {
    val mapper = new ClassProbesMapper()
    val visitor = new ClassProbesAdapter(mapper, false)
    reader.accept(visitor, 0)
    mapper.classLineToBranchExp
  }
}
