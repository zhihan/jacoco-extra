package me.zhihan.jacoco;

public class MyInteger {
  private int i;

  public void setI(int i) {
    this.i = i;
  }

  public int getSign() {
    if (i > 0) {
      return 1;
    } else {
      return -1;
    }
  }
}
