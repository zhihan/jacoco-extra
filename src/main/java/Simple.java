package me.zhihan;

public class Simple {
    private int f;
    public int getF() {
        return f;
    }

    public Integer getObj() {
        return f;
    }

    public void setF(int i) {
        // Local variables:
        // 0 - this, 1 - i, 2 - result
        // iconst_2
        // iload_1
        // imul
        // istore_2
        int result = 2 * i;
        // bipush 6
        // istore_1
        i = 6;
        // getstatic #4
        // iload_1
        // invokevirtual
        System.out.println(i);
        // aload_0
        // iload_2
        // putfield #2
        f = result;
    }
}
