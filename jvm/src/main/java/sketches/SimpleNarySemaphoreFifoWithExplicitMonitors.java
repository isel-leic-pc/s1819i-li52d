package sketches;

import pt.isel.pc.examples.utils.NodeLinkedList;
import pt.isel.pc.examples.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleNarySemaphoreFifoWithExplicitMonitors
        implements NarySemaphore {

    private static class Request {
        public final int requestedUnits;
        public final Condition condition;

        public Request(int requestedUnits, Condition condition) {

            this.requestedUnits = requestedUnits;
            this.condition = condition;
        }
    }

    private int units;
    private final Lock monitor = new ReentrantLock();
    private final NodeLinkedList<Request> q = new NodeLinkedList<>();

    public SimpleNarySemaphoreFifoWithExplicitMonitors(int initial) {
        units = initial;
    }

    @Override
    public boolean tryAcquire(int requestedUnits, long timeoutInMs) throws InterruptedException {

        NodeLinkedList.Node<Request> node = null;
        try {
            monitor.lock();

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
            node = q.push(
                    new Request(requestedUnits, monitor.newCondition()));
            long limit = Timeouts.start(timeoutInMs);
            long remainingInMs = Timeouts.remaining(limit);

            while (true) {
                node.value.condition.await(remainingInMs, TimeUnit.MILLISECONDS);
                if (q.isHeadNode(node) && units >= requestedUnits) {
                    units -= requestedUnits;
                    return true;
                }
                remainingInMs = Timeouts.remaining(limit);
                if (Timeouts.isTimeout(remainingInMs)) {
                    return false;
                }
            }
        } finally {
            if (node != null) {
                q.remove(node);
                notifyIfNeeded();
            }
            monitor.unlock();
        }
    }

    @Override
    public void release(int releasedUnits) {
        try {
            monitor.lock();
            units += releasedUnits;
            // notifyIfNeeded();
        } finally {
            monitor.unlock();
        }
    }

    private void notifyIfNeeded() {
        if (!q.isEmpty() && units >= q.getHeadValue().requestedUnits) {
            q.getHeadValue().condition.signal();
        }
    }
}
