package org.jacoco.extra.internal;

import java.util.List;
import java.util.ArrayList;

/**
 * A CovExp is an expression used to evaluate the coverage of a branch.
 * It can be either a probeId or a recursive combination of probeIds.
 */
public abstract class CovExp {

  /** Create a new BranchExp using this CovExp as the only branch. */
  public BranchExp branchExp() {
    List<CovExp> branches = new ArrayList<CovExp>();
    branches.add(this);
    return new BranchExp(branches);
  }

  /** Evaluate the expression using the given values of probes. */
  public abstract boolean eval(final boolean[] probes);
}
