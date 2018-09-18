package pt.isel.pc.examples.synchronizers;

import pt.isel.pc.examples.utils.NodeLinkedList;
import pt.isel.pc.examples.utils.Timeouts;

public class FifoSemaphore {

    private long units;
    private final Object mon = new Object();
    private final NodeLinkedList<Integer> q = new NodeLinkedList<>();

    public FifoSemaphore(long initial) {

        units = initial;
    }

    public boolean acquire(int requested, long timeout) throws InterruptedException {
        synchronized (mon) {
            if (q.isEmpty() && this.units >= requested) {
                this.units -= requested;
                return true;
            }

            if (Timeouts.noWait(timeout)) {
                return false;
            }

            long t = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(t);
            NodeLinkedList.Node<Integer> node = q.push(requested);
            while (true) {

                try {
                    mon.wait(remaining);
                } catch (InterruptedException e) {
                    q.remove(node);
                    notifyIfNeeded();
                    throw e;
                }

                if (q.isHeadNode(node) && this.units >= requested) {
                    this.units -= requested;
                    q.remove(node);
                    notifyIfNeeded();
                    return true;
                }

                remaining = Timeouts.remaining(t);
                if (Timeouts.isTimeout(remaining)) {
                    q.remove(node);
                    notifyIfNeeded();
                    return false;
                }
                notifyIfNeeded();
            }
        }
    }

    public void release(int units) {
        synchronized (mon) {
            this.units += units;
            notifyIfNeeded();
        }
    }

    private void notifyIfNeeded() {
        if (!q.isEmpty() && units >= q.getHeadValue()) {
            mon.notify();
        }
    }
}
