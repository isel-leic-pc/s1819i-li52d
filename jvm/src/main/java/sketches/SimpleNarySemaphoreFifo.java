package sketches;

import pt.isel.pc.examples.synchronizers.NarySemaphore;
import pt.isel.pc.examples.utils.NodeLinkedList;
import pt.isel.pc.examples.utils.Timeouts;

public class SimpleNarySemaphoreFifo implements NarySemaphore {

    private int units;
    private final Object monitor = new Object();
    private final NodeLinkedList<Integer> q = new NodeLinkedList<>();

    public SimpleNarySemaphoreFifo(int initial) {
        units = initial;
    }

    @Override
    public boolean tryAcquire(int requestedUnits, long timeoutInMs) throws InterruptedException {

        synchronized (monitor) {
            // fast path
            if (q.isEmpty() && units >= requestedUnits) {
                units -= requestedUnits;
                return true;
            }

            // should wait or not?
            if (Timeouts.noWait(timeoutInMs)) {
                return false;
            }

            // prepare everything for waiting
            NodeLinkedList.Node<Integer> node = q.push(requestedUnits);
            long limit = Timeouts.start(timeoutInMs);
            long remainingInMs = Timeouts.remaining(limit);

            while (true) {
                try {
                    monitor.wait(remainingInMs);
                }catch(InterruptedException e) {
                    q.remove(node);
                    notifyIfNeeded();
                    throw e;
                }

                if (q.isHeadNode(node) && units >= requestedUnits) {
                    units -= requestedUnits;
                    q.remove(node);
                    notifyIfNeeded();
                    return true;
                }
                remainingInMs = Timeouts.remaining(limit);
                if (Timeouts.isTimeout(remainingInMs)) {
                    q.remove(node);
                    notifyIfNeeded();
                    return false;
                }
            }
        }
    }

    @Override
    public void release(int releasedUnits) {
        synchronized (monitor) {
            units += releasedUnits;
            notifyIfNeeded();
        }
    }

    private void notifyIfNeeded () {
        if(!q.isEmpty() && units >= q.getHeadValue()) {
            monitor.notifyAll();
        }
    }
}
