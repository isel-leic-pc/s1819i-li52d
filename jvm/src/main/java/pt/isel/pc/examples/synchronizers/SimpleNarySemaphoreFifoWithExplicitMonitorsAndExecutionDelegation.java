package pt.isel.pc.examples.synchronizers;

import pt.isel.pc.examples.utils.NodeLinkedList;
import pt.isel.pc.examples.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleNarySemaphoreFifoWithExplicitMonitorsAndExecutionDelegation
        implements NarySemaphore {

    private static class Request {
        public final int requestedUnits;
        public final Condition condition;
        public boolean isDone = false;

        public Request(int requestedUnits, Condition condition) {

            this.requestedUnits = requestedUnits;
            this.condition = condition;
        }
    }

    private int units;
    private final Lock monitor = new ReentrantLock();
    private final NodeLinkedList<Request> q = new NodeLinkedList<>();

    public SimpleNarySemaphoreFifoWithExplicitMonitorsAndExecutionDelegation(int initial) {

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
                try {
                    node.value.condition.await(remainingInMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (node.value.isDone) {
                        // restore interrupted status and leave without exception
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    q.remove(node);
                    notifyIfNeeded();
                    throw e;
                }
                if (node.value.isDone) {
                    // if isDone is true, then all the leave processing is already done
                    return true;
                }
                remainingInMs = Timeouts.remaining(limit);
                if (Timeouts.isTimeout(remainingInMs)) {
                    q.remove(node);
                    notifyIfNeeded();
                    return false;
                }
            }
        } finally {
            monitor.unlock();
        }
    }

    @Override
    public void release(int releasedUnits) {
        try {
            monitor.lock();
            units += releasedUnits;
            notifyIfNeeded();
        } finally {
            monitor.unlock();
        }
    }

    private void notifyIfNeeded() {
        while (!q.isEmpty() && units >= q.getHeadValue().requestedUnits) {
            // The signaling thread does the processing
            // - remove the units
            // - and remove from queue
            // on behalf of the signaled thread
            NodeLinkedList.Node<Request> node = q.pull();
            node.value.isDone = true;
            node.value.condition.signal();
            units -= node.value.requestedUnits;
        }
    }

}
