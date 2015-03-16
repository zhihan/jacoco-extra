package me.zhihan.jacoco.internal

import org.jacoco.core.internal.flow.MethodProbesVisitor
import org.jacoco.core.internal.flow.IFrame
import org.jacoco.core.internal.flow.IProbeIdGenerator
import org.jacoco.core.internal.flow.Instruction
import org.jacoco.core.internal.flow.LabelInfo
import org.objectweb.asm.Handle
import org.objectweb.asm.Label

import scala.collection.mutable.Map
import scala.collection.mutable.Set
import scala.collection.mutable.ArrayBuffer
/** 
  *  
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
  private var id = 0
  override def nextId = {
    val r = id
    id += 1
    r
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


class Jump(i: Instruction, l:Label) {
  val source = i
  val target = l
}

/**
  *  Assuming LabelInfo is available!
  */
class MethodProbesMapper extends MethodProbesVisitor {

  var lastInstruction: Instruction = null

  var currentLine: Int = -1
  var lastLine: Int = -1
  var firstLine: Int = -1

  // Probes to the predecessors of the probes
  val probeToInst: Map[Int, Instruction] = Map()

  // A local cache of predecessors as this info is not exposed in Jacoco.
  val pred: Map[Instruction, Instruction] = Map() 
  val lineToProbes: Map[Int, Set[Int]] = Map()

  val instructions: ArrayBuffer[Instruction] = ArrayBuffer()
  val jumps: ArrayBuffer[Jump] = ArrayBuffer()
  val currentLabels: ArrayBuffer[Label] = ArrayBuffer()

  val labelToInstruction: Map[Label, Instruction] = Map()

  /**
    *  Add a new instruction to the end of the 
    */
  private def addNewInstruction {
    val instruction = new Instruction(currentLine)
    instructions.append(instruction)
    if (lastInstruction != null) {
      instruction.setPredecessor(lastInstruction)
      pred += instruction -> lastInstruction
    }

    val labelCount = currentLabels.size;

    currentLabels.foreach{ label =>
      labelToInstruction += label -> instruction
    }
    currentLabels.clear;
    lastInstruction = instruction
  }

  /**
    *  Plain instructions without any probes
    */
  override def visitInsn(opcode: Int) {
    addNewInstruction
  }

  override def visitIntInsn(opcode: Int, operand: Int) {
    addNewInstruction
  }

  override def visitVarInsn(opcode: Int, variable: Int) {
    addNewInstruction
  }

  override def visitTypeInsn(opcode: Int, ty: String) {
    addNewInstruction
  }

  override def visitFieldInsn(opcode: Int, owner: String, name: String, desc:String) {
    addNewInstruction
  }

  override def visitMethodInsn(opcode: Int, owner: String, name: String, 
    desc: String, itf:Boolean) {
    addNewInstruction
  }

  override def visitInvokeDynamicInsn(name: String, desc: String, handle: Handle,
    args: Object*) {
    addNewInstruction
  }
    
  override def visitJumpInsn(opcode: Int, label: Label) {
    println("Add a jump instruction")
    addNewInstruction
    assert(lastInstruction != null)
    assert(label != null)
    jumps.append(new Jump(lastInstruction, label))
  }

  override def visitLdcInsn(cst: Any) {
    addNewInstruction
  }

  override def visitIincInsn(v:Int, inc: Int) {
    addNewInstruction
  }

  override def visitMultiANewArrayInsn(desc: String, dims: Int) {
    addNewInstruction
  }

  override def visitLabel(label: Label) {
    currentLabels.append(label)
    if (!LabelInfo.isSuccessor(label)) {
      lastInstruction = null;
    }
  }

  def addProbe(probeId: Int) {
    // We do not add probes to the flow graph, but we need to update
    // the branch count of the predecessor of the probe
    lastInstruction.addBranch
    probeToInst += probeId -> lastInstruction
  }

  override def visitProbe(probeId: Int) {
    println(s"visiting probe $probeId")

    // This function is only called when visiting a merge node which
    // is a successor.
    // It adds an probe point to the last instruction
    assert(lastInstruction != null)

    addProbe(probeId)
    lastInstruction = null // Merge point should have no predecessor.
  }

  override def visitJumpInsnWithProbe(opcode: Int, label:Label,
    probeId: Int, frame:IFrame) {
    println("visiting jump instrumentation with probe")
    addNewInstruction
    addProbe(probeId)
  }

  override def visitInsnWithProbe(opcode: Int, probeId: Int) {
    println("visiting instn with probe")
    addNewInstruction
    addProbe(probeId)
  }

  override def visitTableSwitchInsnWithProbes(min: Int, max:Int,
    dflt:Label, labels: Array[Label], frame: IFrame) {
    println("visiting table switch")
  }

  override def visitLookupSwitchInsnWithProbes(dflt: Label,
    keys: Array[Int], labels: Array[Label], frame: IFrame) {
    println("visiting lookup switch")
  }

  override def visitLineNumber(line: Int, start: Label) {
    currentLine = line
    if (lastLine < 0 || firstLine > line) {
      firstLine = line
    }
    if (lastLine < line) {
      lastLine = line
    }
  }

  /** Finishing the method */
  override def visitEnd {

    jumps.foreach{
      jump => {
        val insn = labelToInstruction(jump.target)
        insn.setPredecessor(jump.source)
        pred += insn -> jump.source
      }
    }

    probeToInst.foreach {
      case(probeId, i) => {
        var insn = i
        while (insn != null) {
          if (lineToProbes.contains(insn.getLine)) {
            lineToProbes(insn.getLine) += probeId
          } else {
            lineToProbes += insn.getLine -> Set(probeId)
          }

          if (insn.getBranches > 1) {
            insn = null // break at branches
          } else {
            insn = pred.getOrElse(insn, null)
          }
        }
      }
    }

  }

}


