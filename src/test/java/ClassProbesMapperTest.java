package org.jacoco.extra.internal;

import org.junit.Test;
import org.junit.Before;
import org.junit.Assert;

import org.objectweb.asm.Opcodes;
import org.jacoco.core.internal.flow.MethodProbesVisitor;

import java.util.Map;

public class ClassProbesMapperTest {
    private ClassProbesMapper mapper;

    @Before
    public void setup() {
        mapper = new ClassProbesMapper();
        mapper.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC, "Foo", null,
            "java/lang/Object", null);
    }

    @Test
    public void testEmptyMethod() {
        MethodProbesVisitor mv = mapper.visitMethod(0, "foo", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitEnd();

        Map<Integer, BranchExp> result = mapper.result();
        Assert.assertTrue(result.isEmpty());
    }
}
