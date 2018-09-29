package sketches;

import pt.isel.pc.examples.synchronizers.NarySemaphore;
import pt.isel.pc.examples.utils.Timeouts;

public class SimpleNarySemaphore implements NarySemaphore {

    private int units;
    private final Object monitor = new Object();

    public SimpleNarySemaphore(int initial) {
        units = initial;
    }

    @Override
    public boolean tryAcquire(int requestedUnits, long timeoutInMs) throws InterruptedException {

        synchronized (monitor) {
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
                monitor.wait(remainingInMs);

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
    }

    @Override
    public void release(int releasedUnits) {
        synchronized (monitor) {
            units += releasedUnits;
            monitor.notifyAll();
        }
    }
}
