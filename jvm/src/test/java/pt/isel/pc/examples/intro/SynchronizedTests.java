package pt.isel.pc.examples.intro;

import org.junit.Test;

public class SynchronizedTests {

    @Test
    public void test() {
        Object obj = new Object();
        synchronized (obj) {
            // ...
        }
    }

}
