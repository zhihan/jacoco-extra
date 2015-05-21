package me.zhihan.jacoco.internal

import org.jacoco.core.internal.flow.{MethodProbesVisitor, IFrame, 
  IProbeIdGenerator, Instruction, LabelInfo, ClassProbesVisitor, 
  ClassProbesAdapter}
import org.objectweb.asm.{Handle, Label, FieldVisitor, ClassReader}
import scala.collection.mutable.{Map, MultiMap, HashMap, Set, ArrayBuffer}

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
  * In the same manner, if we provide different visitors we might be able
  * to map out correspondence between probes and branch instructions. 
  */

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

/**
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
class Jump(val source: Instruction, val target:Label) {}

abstract class ProbeExp {
  def probes: ProbeExp
}

class SingleProbe(val id: Int) extends ProbeExp {
  def probes = new Probes(ArrayBuffer(this))
}

class Probes(val branches: ArrayBuffer[ProbeExp]) extends ProbeExp {
  def probes = new Probes(ArrayBuffer(this))

  def add(e:ProbeExp): Int = {
    branches.append(e)
    branches.size - 1
  }

  def update(i: Int, e: ProbeExp) {
    branches(i) = e
  }

  def join(e: Probes) {
    branches ++= e.branches
  }
}

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
  val insnToProbes: Map[Instruction, ProbeExp] = Map()

  val lineToProbes: Map[Int, Probes] = Map()

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
    println(s"Add probe to $lastInstruction")
    lastInstruction.addBranch
    probeToInsn += probeId -> lastInstruction
  }

  override def visitProbe(probeId: Int) {
    // This function is only called when visiting a merge node which
    // is a successor.
    // It adds an probe point to the last instruction
    assert(lastInstruction != null)
    println("merge scope")
    addProbe(probeId)
    lastInstruction = null // Merge point should have no predecessor.
  }

  override def visitJumpInsnWithProbe(opcode: Int, label:Label,
    probeId: Int, frame:IFrame) {
    visitInstruction  // This is not a typo
    println("Jump with probe is not a jump!")
    addProbe(probeId)
  }

  override def visitInsnWithProbe(opcode: Int, probeId: Int) {
    visitInstruction
    println("Insn with probe")
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
    println(s" Total ${jumps.size} jumps.")
    jumps.foreach{ jump =>
      {
        val insn = labelToInstruction(jump.target)
        println(s"Add branch to ${jump.source}")
        insn.setPredecessor(jump.source)
        pred += insn -> jump.source        
      }
    }

    probeToInsn.foreach { case(probeId, instruction) => {
      var insn = instruction
      var exp: ProbeExp = new SingleProbe(probeId)
      // The instruction associated with the probe
      insnToProbes += (insn -> exp)
      while (insn != null) {
        val predecessor = pred.getOrElse(insn, null)
        if (predecessor != null) {
          insn = predecessor
          if (predecessor.getBranches > 1) {
            // Predecessor is a branch point
            var predInsn = insnToProbes.getOrElse(predecessor, null)
            if (predInsn == null) {
              // First time visit predecessor
              predInsn = exp.probes
              insnToProbes += predecessor -> predInsn
            } else {
              var idx = insnToIdx.getOrElse(insn, -1)
              val probes = predInsn.asInstanceOf[Probes]
              if (idx >= 0) {
                // Update
                probes.update(idx, exp)
              } else {
                // New branch
                idx = probes.add(exp)
                insnToIdx += (insn -> idx)
              }
              // Update
            }
            exp = predInsn
          } else {
            insnToProbes += (predecessor -> exp)
          }
          insn = predecessor
        } else {
          insn = null // break
        }
      }
    }
    }

    instructions.foreach { insn =>
      if (insn.getBranches > 1) {
        // Add to the line branches
        var probes = lineToProbes.getOrElse(insn.getLine, null)
        if (probes == null) {
          probes = insnToProbes(insn).asInstanceOf[Probes]
          lineToProbes(insn.getLine) = probes
        } else {
          probes.join(
            insnToProbes(insn).asInstanceOf[Probes])
        }
      }
    }
  }
}

/** Class probes mapper that computes a map from lines to probe ids.*/
class ClassProbesMapper extends ClassProbesVisitor {
  val classLineToProbes = new HashMap[Int, Set[Int]]() with MultiMap[Int, Int]

  /** Create a method probes mapper and analyze a method */
  override def visitMethod(access: Int, name: String,
    desc: String, signature: String, 
    exceptions: Array[String]): MethodProbesVisitor =
    new MethodProbesMapper {
      override def visitEnd {
        super.visitEnd
    //    classLineToProbes ++= lineToProbes
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
  def analyzeClass(reader: ClassReader): MultiMap[Int, Int] = {
    val mapper = new ClassProbesMapper()
    val visitor = new ClassProbesAdapter(mapper, false)
    reader.accept(visitor, 0)
    mapper.classLineToProbes
  }
}
