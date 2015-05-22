package org.jacoco.extra.internal;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.Before;
import org.junit.Assert;

public class CovExpTest {
	@Test
	public void testBranchExp() {
		CovExp a = new ProbeExp(0);
		BranchExp actual = a.branchExp();
		assertThat(actual.getBranches().size()).isEqualTo(1);
        assertThat(actual.getBranches().contains(a));
	}

    @Test
    public void testAdd() {
        CovExp a = new ProbeExp(0);
        BranchExp b = a.branchExp();
        CovExp c = new ProbeExp(1);
        int idx = b.add(c);
        assertThat(idx).isEqualTo(1);
        assertThat(b.getBranches()).contains(a);
        assertThat(b.getBranches()).contains(c);
    }

    @Test
    public void testUpdate() {
        CovExp a = new ProbeExp(0);
        BranchExp b = a.branchExp();
        CovExp c = new ProbeExp(1);
        b.update(0, c);
        assertThat(b.getBranches().size()).isEqualTo(1);
        assertThat(b.getBranches()).contains(c);
    }

    @Test
    public void testMerge() {
        CovExp a = new ProbeExp(0);
        BranchExp b = a.branchExp();
        CovExp c = new ProbeExp(1);
        BranchExp d = c.branchExp();
        b.merge(d);
        assertThat(b.getBranches().size()).isEqualTo(2);
        assertThat(b.getBranches()).containsAllOf(a, c);
    }

}
