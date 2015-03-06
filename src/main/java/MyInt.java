package org.jacoco.examples;

public  class MyInt implements MyIntI {
    Integer i = 0;

    public Integer getI() {
      if (i>0) {
          return 1;
      } else {
          return 0;
      }
    }
}
