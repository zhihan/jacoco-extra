package org.jacoco.extra.internal;

import com.google.common.collect.Multimap;
import com.google.common.collect.HashMultimap;

import org.jacoco.core.internal.flow.MethodProbesVisitor;
import org.jacoco.core.internal.flow.ClassProbesVisitor;

import org.objectweb.asm.FieldVisitor;

import java.util.HashMap;
import java.util.Map;

/** 
 * A visitor that maps each source code line to the probes corresponding to the lines. 
 */
public class ClassProbesMapper extends ClassProbesVisitor {
    private Multimap<Integer, Integer> classLineToProbes;
    public Multimap<Integer, Integer> result() {
        return classLineToProbes;
    }

    /** Create a new probe mapper object. */
    public ClassProbesMapper() {
        classLineToProbes = HashMultimap.<Integer, Integer>create();
    }

    /** Returns a visitor for mapping method code. */
    @Override
    public MethodProbesVisitor visitMethod(int access, String name, String desc, String signature, 
        String[] exceptions) {
        return new MethodProbesMapper() {
            @Override
            public void visitEnd() {
                super.visitEnd();

                for (Map.Entry<Integer, Integer> entry: result().entries()) {
                    classLineToProbes.put(entry.getKey(), entry.getValue());   
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
