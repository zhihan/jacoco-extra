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
    assertThat(actual.getBranches()).containsExactly(a);

    BranchExp nested = actual.branchExp();
    assertThat(nested.getBranches()).containsExactly(actual);
  }

  @Test
  public void testAdd() {
    CovExp a = new ProbeExp(0);
    CovExp c = new ProbeExp(1);

    BranchExp b = a.branchExp();
    int idx = b.add(c);

    assertThat(idx).isEqualTo(1);
    assertThat(b.getBranches()).containsExactly(a, c);
  }

  @Test
  public void testUpdate() {
    CovExp a = new ProbeExp(0);
    CovExp c = new ProbeExp(1);
    BranchExp b = a.branchExp();

    b.update(0, c);
    assertThat(b.getBranches()).containsExactly(c);
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

  @Test
  public void testEvaluate() {
    CovExp a = new ProbeExp(0);
    CovExp b = a.branchExp();
    CovExp c = new ProbeExp(1);
    BranchExp d = a.branchExp();
    d.add(c);

    boolean[] probes = {true, false};

    assertThat(a.eval(probes)).isEqualTo(true);
    assertThat(b.eval(probes)).isEqualTo(true);
    assertThat(c.eval(probes)).isEqualTo(false);
    assertThat(d.eval(probes)).isEqualTo(true);
  }
}
