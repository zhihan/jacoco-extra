package me.zhihan.jacoco.internal;

public class MyC implements MyI {
  public MyC() {
    // Default contructor
  }

  @Override
  public int f(int x) {
    int y = 0;
    if (x < 0) { // This line should have two branches
      y = -1;
    } else {
      y = 1;
    }
    return y;
  }
}
