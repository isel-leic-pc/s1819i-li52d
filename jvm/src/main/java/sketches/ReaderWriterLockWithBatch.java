package sketches;

import pt.isel.pc.examples.utils.Batch;
import pt.isel.pc.examples.utils.NodeLinkedList;
import pt.isel.pc.examples.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ReaderWriterLockWithBatch {

    private static class WriteRequest {
        public boolean wasAllowed;
        public final Condition cond;

        public WriteRequest(Condition cond) {
            this.cond = cond;
        }
    }

    private boolean isWriting;
    private int nOfReaders;
    private final NodeLinkedList<WriteRequest> wrq = new NodeLinkedList<>();
    private final Lock mon = new ReentrantLock();
    private Batch rdBatch = new Batch(mon.newCondition());

    public boolean startRead(long timeout) throws InterruptedException {
        try {
            mon.lock();
            // fast-path
            if (!isWriting && wrq.isEmpty()) {
                nOfReaders += 1;
                return true;
            }
            if (Timeouts.noWait(timeout)) {
                return false;
            }
            long limit = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(limit);
            Batch myBatch = rdBatch.add();
            while (true) {
                try {
                    myBatch.getCondition().await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (myBatch.isDone()) {
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    myBatch.cancel();
                    throw e;
                }
                if (myBatch.isDone()) {
                    return true;
                }
                remaining = Timeouts.remaining(limit);
                if (Timeouts.isTimeout(remaining)) {
                    myBatch.cancel();
                    return false;
                }
            }

        } finally {
            mon.unlock();
        }
    }

    public boolean startWrite(long timeout) throws InterruptedException {
        try {
            mon.lock();
            // fast-path
            if (!isWriting && nOfReaders == 0) {
                isWriting = true;
                return true;
            }
            if (Timeouts.noWait(timeout)) {
                return false;
            }
            long limit = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(limit);
            NodeLinkedList.Node<WriteRequest> node = wrq.push(
                    new WriteRequest(mon.newCondition()));
            while (true) {
                try {
                    node.value.cond.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (node.value.wasAllowed) {
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    cancelWriter(node);
                    throw e;
                }
                if (node.value.wasAllowed) {
                    return true;
                }
                remaining = Timeouts.remaining(limit);
                if (Timeouts.isTimeout(remaining)) {
                    cancelWriter(node);
                    return false;
                }
            }

        } finally {
            mon.unlock();
        }
    }

    private void cancelWriter(NodeLinkedList.Node<WriteRequest> node) {
        wrq.remove(node);
        if(!isWriting && wrq.isEmpty() && rdBatch.hasAny()) {
            int nOfReadersInBatch = rdBatch.complete();
            nOfReaders += nOfReadersInBatch;
            rdBatch = new Batch(mon.newCondition());
        }
    }

    public void endRead() {
        try {
            mon.lock();
            nOfReaders -= 1;
            if (nOfReaders == 0 && !wrq.isEmpty()) {
                NodeLinkedList.Node<WriteRequest> writer = wrq.pull();
                writer.value.wasAllowed = true;
                isWriting = true;
                writer.value.cond.signal();
            }
        } finally {
            mon.unlock();
        }
    }

    public void endWrite() {
        try {
            mon.lock();
            isWriting = false;
            if (rdBatch.hasAny()) {
                int nOfReadersInBatch = rdBatch.complete();
                nOfReaders += nOfReadersInBatch;
                rdBatch = new Batch(mon.newCondition());
            } else {
                if (!wrq.isEmpty()) {
                    NodeLinkedList.Node<WriteRequest> writer = wrq.pull();
                    writer.value.wasAllowed = true;
                    isWriting = true;
                    writer.value.cond.signal();
                }
            }
        } finally {
            mon.unlock();
        }
    }

}
