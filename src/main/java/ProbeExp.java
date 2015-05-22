package org.jacoco.extra.internal;

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