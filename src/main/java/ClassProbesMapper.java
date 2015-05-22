package org.jacoco.extra.internal;

import org.jacoco.core.internal.flow.MethodProbesVisitor;
import org.jacoco.core.internal.flow.ClassProbesVisitor;

import org.objectweb.asm.FieldVisitor;

import java.util.HashMap;
import java.util.Map;

/**
 * A visitor that maps each source code line to the probes corresponding to the lines.
 */
public class ClassProbesMapper extends ClassProbesVisitor {
  private Map<Integer, BranchExp> classLineToBranchExp;
  public Map<Integer, BranchExp> result() {
    return classLineToBranchExp;
  }

  /** Create a new probe mapper object. */
  public ClassProbesMapper() {
    classLineToBranchExp = new HashMap<Integer, BranchExp>();
  }

  /** Returns a visitor for mapping method code. */
  @Override
  public MethodProbesVisitor visitMethod(int access, String name, String desc, String signature,
      String[] exceptions) {
    return new MethodProbesMapper() {
      @Override
      public void visitEnd() {
        super.visitEnd();

        for (Map.Entry<Integer, BranchExp> entry: result().entrySet()) {
          BranchExp branchExp = classLineToBranchExp.get(entry.getKey());
          if (branchExp == null) {
            classLineToBranchExp.put(entry.getKey(), entry.getValue());
          } else {
            branchExp.merge(entry.getValue());
          }
        }
      }
    };
  }

  /** Returns a visitor for field. */
    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature,
        Object value) {
      return super.visitField(access, name, desc, signature, value);
    }

  /** Visit total probe count. */
  @Override
  public void visitTotalProbeCount(int count) {
    // Nothing to do. Maybe perform some sanity checks here.
  }
}
