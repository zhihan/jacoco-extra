package me.zhihan.jacoco;

public class MyInteger {
  private int i;

  static private boolean[] flags;

  static public void myInit() {
    flags = new boolean[2];
  }

  public void setI(int i) {
    this.i = i;
  }

  public int getSign() {
    if (i > 0) {
      return 1;
    } else if (i == 0) {
      return 0;
    } else {
      return -1;
    }
  }
}
