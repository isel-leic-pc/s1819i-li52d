package sketches;

import pt.isel.pc.examples.utils.Timeouts;

public class SimpleNarySemaphore {

    private int units;

    public SimpleNarySemaphore(int initial) {
        units = initial;
    }

    // synchronized over this
    synchronized public boolean tryAcquire(int requestedUnits, long timeoutInMs) throws InterruptedException {

        // fast path
        if (units >= requestedUnits) {
            units -= requestedUnits;
            return true;
        }

        // should wait or not?
        if (Timeouts.noWait(timeoutInMs)) {
            return false;
        }

        // prepare everything for waiting
        long limit = Timeouts.start(timeoutInMs);
        long remainingInMs = Timeouts.remaining(limit);
        while (true) {

            // no interrupt cancellation processing needed
            // due to the notifyAll
            this.wait(remainingInMs);

            if (units >= requestedUnits) {
                units -= requestedUnits;
                return true;
            }
            remainingInMs = Timeouts.remaining(limit);
            if (Timeouts.isTimeout(remainingInMs)) {
                // no cancellation processing needed
                return false;
            }
        }
    }

    // synchronized over this
    synchronized public void release(int releasedUnits) {
        units += releasedUnits;
        this.notifyAll();
    }
}
