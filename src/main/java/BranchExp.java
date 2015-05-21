package org.jacoco.extra.internal;

import java.util.List;
import java.util.ArrayList;

public class BranchExp extends CovExp {
    private final List<CovExp> branches;
    private boolean hasValue;
    private boolean value;

    public BranchExp(List<CovExp> branches) {
        this.branches = branches;
        hasValue = false;
    }

    public List<CovExp> getBranches() {
        return branches;
    }

    /** Add a branch expression. */
    public int add(CovExp exp) {
        branches.add(exp);
        return branches.size() - 1;
    }

    /** Update an existing branch expression. */
    public void update(int idx, CovExp exp) {
        branches.set(idx, exp);
    }

    /** Make a union of the the branches of two BranchExp. */
    public void merge(BranchExp other) {
        branches.addAll(other.branches);
    }

    @Override
    public boolean eval(final boolean[] probes) {
        if (hasValue) {
            return value;
        }
        value = false;
        for (CovExp exp: branches) {
            value = exp.eval(probes);
            if (value) {
                break;
            }
        }
        hasValue = value; // cached
        return value;
    }
}