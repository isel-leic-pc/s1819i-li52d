package pt.isel.pc.examples.synchronizers;

import pt.isel.pc.examples.utils.BatchQueue;
import pt.isel.pc.examples.utils.NodeLinkedList;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleReaderWriterLockWithBatchQueue implements ReaderWriterLock {

    private int nOfReaders = 0;
    private boolean isWriting = false;
    private final Lock mon = new ReentrantLock();
    private final Condition cond = mon.newCondition();

    private static class Request {
        public boolean isAllowed = false;
    }

    private final BatchQueue rdq = new BatchQueue();
    private final NodeLinkedList<Request> wrq = new NodeLinkedList<>();

    @Override
    public void decorate(Decorable.ERunnable runnable) throws InterruptedException {
        try{
            mon.lock();
            runnable.run();
        } finally {
            mon.unlock();
        }
    }

    @Override
    public void startRead() throws InterruptedException {
        try {
            mon.lock();
            if (wrq.isEmpty() && !isWriting) {
                nOfReaders += 1;
                return;
            }
            int batchNumber = rdq.addElementToBatch();
            while (!rdq.isBatchCompleted(batchNumber)) {
                try {
                    cond.await();
                } catch (InterruptedException e) {
                    if (rdq.isBatchCompleted(batchNumber)) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    rdq.removeElementFromBatch(batchNumber);
                    throw e;
                }
            }
        } finally {
            mon.unlock();
        }
    }

    @Override
    public void endRead() {
        try {
            mon.lock();
            nOfReaders -= 1;
            if (nOfReaders == 0 && !wrq.isEmpty()) {
                NodeLinkedList.Node<Request> writer = wrq.pull();
                writer.value.isAllowed = true;
                isWriting = true;
                cond.signalAll();
            }
        } finally {
            mon.unlock();
        }
    }

    @Override
    public void startWrite() throws InterruptedException {
        try {
            mon.lock();
            if (nOfReaders == 0 && !isWriting) {
                isWriting = true;
                return;
            }
            NodeLinkedList.Node<Request> node = wrq.push(new Request());
            while (!node.value.isAllowed) {
                try {
                    cond.await();
                } catch (InterruptedException e) {
                    if (node.value.isAllowed) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    wrq.remove(node);
                    doWriterCancellation();
                    throw e;
                }
            }
        } finally {
            mon.unlock();
        }
    }

    @Override
    public void endWrite() {
        try {
            mon.lock();
            isWriting = false;
            if (!rdq.isCurrentBatchEmpty()) {
                int readers = rdq.completeBatchAndGetNumberOfElements();
                nOfReaders += readers;
                cond.signalAll();
            } else {
                if(!wrq.isEmpty()) {
                    NodeLinkedList.Node<Request> writer = wrq.pull();
                    writer.value.isAllowed = true;
                    isWriting = true;
                    cond.signalAll();
                }
            }
        } finally {
            mon.unlock();
        }
    }

    private void doWriterCancellation() {
        if (!isWriting && wrq.isEmpty()) {
            if(!rdq.isCurrentBatchEmpty()) {
                int readers = rdq.completeBatchAndGetNumberOfElements();
                nOfReaders += readers;
                cond.signalAll();
            }
            cond.signalAll();
        }
    }
}
