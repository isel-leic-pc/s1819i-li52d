package pt.isel.pc.examples.synchronizers;

import pt.isel.pc.examples.utils.NodeLinkedList;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleReaderWriterLock implements ReaderWriterLock {

    private int nOfReaders = 0;
    private boolean isWriting = false;
    private final Lock mon = new ReentrantLock();
    private final Condition cond = mon.newCondition();

    @Override
    public void decorate(ERunnable runnable) throws InterruptedException {
        try{
            mon.lock();
            runnable.run();
        } finally {
            mon.unlock();
        }
    }

    private static class Request {
        public boolean isAllowed = false;
    }

    private final NodeLinkedList<Request> rdq = new NodeLinkedList<>();
    private final NodeLinkedList<Request> wrq = new NodeLinkedList<>();

    public void startRead() throws InterruptedException {
        try {
            mon.lock();
            if (wrq.isEmpty() && !isWriting) {
                nOfReaders += 1;
                return;
            }
            NodeLinkedList.Node<Request> node = rdq.push(new Request());
            while (!node.value.isAllowed) {
                try {
                    cond.await();
                } catch (InterruptedException e) {
                    if (node.value.isAllowed) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    rdq.remove(node);
                    throw e;
                }
            }
        } finally {
            mon.unlock();
        }
    }

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
                    doWriterCancellation(node);
                    throw e;
                }
            }
        } finally {
            mon.unlock();
        }
    }

    public void endWrite() {
        try {
            mon.lock();
            isWriting = false;
            if (!rdq.isEmpty()) {
                while (!rdq.isEmpty()) {
                    NodeLinkedList.Node<Request> reader = rdq.pull();
                    reader.value.isAllowed = true;
                    nOfReaders += 1;
                }
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

    private void doWriterCancellation(NodeLinkedList.Node<Request> node) {
        wrq.remove(node);
        if (!isWriting && wrq.isEmpty() && !rdq.isEmpty()) {
            do {
                NodeLinkedList.Node<Request> reader = rdq.pull();
                reader.value.isAllowed = true;
                nOfReaders += 1;
            } while(!rdq.isEmpty());
            cond.signalAll();
        }
    }

}
