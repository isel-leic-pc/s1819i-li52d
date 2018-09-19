package sketches;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

public class SimpleSemaphore {

    private int units;

    public SimpleSemaphore(int initial) {
        units = initial;
    }

    // synchronized over this
    synchronized public void acquire(long timeoutInMs) throws InterruptedException {
        while (units == 0) {
            try {
                // wrong
                this.wait(timeoutInMs);
            }catch(InterruptedException ex) {
                if(units > 0) {
                    notify();
                }
                throw ex;
            }
        }
        // units > 0
        units -= 1;
    }

    // synchronized over this
    synchronized public void release() {
        units += 1;
        this.notify();
    }
}
