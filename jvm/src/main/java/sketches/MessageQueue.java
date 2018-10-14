package sketches;

import pt.isel.pc.examples.utils.NodeLinkedList;
import pt.isel.pc.examples.utils.Timeouts;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MessageQueue<T> {

    private static class Consumer<T> {
        public final Condition condition;
        public T message;

        public Consumer(Condition condition) {
            this.condition = condition;
            message = null;
        }

        public boolean hasMessage() {
            return message != null;
        }

        public void putMessageAndSignal(T message) {
            this.message = message;
            condition.signal();
        }
    }

    private static class Producer<T> {
        private final Condition condition;
        private T message;

        public Producer(Condition condition, T message) {
            this.condition = condition;
            this.message = message;
        }

        public boolean wasDelivered() {
            return message == null;
        }

        public T getMessageAndSignal() {
            T res = message;
            message = null;
            condition.signal();
            return res;
        }
    }

    private final NodeLinkedList<Consumer<T>> consumers = new NodeLinkedList<>();
    private final NodeLinkedList<Producer<T>> producers = new NodeLinkedList<>();
    private final Lock mon = new ReentrantLock();

    public Optional<T> get(long timeout) throws InterruptedException {
        try {
            mon.lock();
            // fast-path
            if (producers.isNotEmpty()) {
                T message = producers.pull().value.getMessageAndSignal();
                return Optional.of(message);
            }
            if (Timeouts.noWait(timeout)) {
                return Optional.empty();
            }
            long limit = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(limit);
            NodeLinkedList.Node<Consumer<T>> node = consumers.push(new Consumer<>(mon.newCondition()));
            do {
                try {
                    node.value.condition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (node.value.hasMessage()) {
                        Thread.currentThread().interrupt();
                        return Optional.of(node.value.message);
                    }
                    consumers.remove(node);
                    throw e;
                }
                if (node.value.hasMessage()) {
                    return Optional.of(node.value.message);
                }
                remaining = Timeouts.remaining(limit);
                if (Timeouts.isTimeout(remaining)) {
                    consumers.remove(node);
                    return Optional.empty();
                }
            } while (true);
        } finally {
            mon.unlock();
        }
    }

    public boolean put(T message, long timeout) throws InterruptedException {
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }
        try {
            mon.lock();
            // fast-path
            if (consumers.isNotEmpty()) {
                consumers.pull().value.putMessageAndSignal(message);
                return true;
            }
            long limit = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(limit);
            NodeLinkedList.Node<Producer<T>> node = producers.push(new Producer<T>(mon.newCondition(), message));
            do {
                try {
                    node.value.condition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if(node.value.wasDelivered()) {
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    producers.remove(node);
                    throw e;
                }
                if(node.value.wasDelivered()) {
                    return true;
                }
                remaining = Timeouts.remaining(limit);
                if(Timeouts.isTimeout(remaining)) {
                    producers.remove(node);
                    return false;
                }
            } while (true);
        } finally {
            mon.unlock();
        }
    }

}
