package org.jacoco.extra.internal;

import java.util.List;
import java.util.ArrayList;

public class ProbeExp extends CovExp {
    private final int probeId;

    public ProbeExp(int id) {
        probeId = id;
    }

    @Override
    public boolean eval(final boolean[] probes) {
        return probes[probeId];
    }
}