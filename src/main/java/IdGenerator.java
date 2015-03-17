 
package org.jacoco.extra.internal;

import org.jacoco.core.internal.flow.IProbeIdGenerator;

/** A simple object that implements the id generator interface */

public class IdGenerator implements IProbeIdGenerator {
  private int id = 0;

  @Override
  public int nextId() {
    return id++;
  }

  public void reset() {
    id = 0;
  }
}
