package pt.isel.pc.examples.synchronizers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.isel.pc.examples.utils.GenericBatchQueue;
import pt.isel.pc.examples.utils.Timeouts;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CyclicBarrier {

    private static final Logger log = LoggerFactory.getLogger(CyclicBarrier.class);

    private enum State {
        open, closed, broken
    }

    private static class StateHolder {
        public State state;
        public StateHolder(State state) {
            this.state = state;
        }
    }

    private final GenericBatchQueue<StateHolder> q;
    private final int parties;
    private int remainingThreads;
    private final Lock mon = new ReentrantLock();
    private final Condition cond = mon.newCondition();

    public CyclicBarrier(int parties) {
        this.parties = parties;
        remainingThreads = parties;
        q = new GenericBatchQueue<>(new StateHolder(State.closed));
    }

    private void openBarrier() {
        if (q.getCount() > 0) {
            q.getCurrentRequest().state = State.open;
            cond.signalAll();
        }
        q.newBatch(new StateHolder(State.closed));
        remainingThreads = parties;
    }

    private void breakBarrier() {
        if (q.getCount() > 0) {
            q.getCurrentRequest().state = State.broken;
            cond.signalAll();
        }
    }

    public int await(long timeout) throws BrokenBarrierException, TimeoutException, InterruptedException {
        try {
            mon.lock();
            if (q.getCurrentRequest().state == State.broken) {
                throw new BrokenBarrierException();
            }
            int index = --remainingThreads;
            if (index == 0) {
                openBarrier();
                return 0;
            }
            StateHolder stateHolder = q.add();
            long limit = Timeouts.start(timeout);
            while (true) {
                long remaining = Timeouts.remaining(limit);
                if (Timeouts.isTimeout(remaining)) {
                    breakBarrier();
                    throw new TimeoutException();
                }
                try {
                    cond.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (stateHolder.state == State.open) {
                        Thread.currentThread().interrupt();
                        return index;
                    }
                    if (stateHolder.state == State.broken) {
                        throw e;
                    }
                    breakBarrier();
                    throw e;
                }
                if (stateHolder.state == State.open) {
                    return index;
                }
                if (stateHolder.state == State.broken) {
                    throw new BrokenBarrierException();
                }
            }
        } finally {
            mon.unlock();
        }
    }

    public void reset() {
        try {
            mon.lock();
            breakBarrier();
            q.newBatch(new StateHolder(State.closed));
            remainingThreads = parties;
        } finally {
            mon.unlock();
        }
    }

    public boolean isBroken() {
        try {
            mon.lock();
            return q.getCurrentRequest().state == State.broken;
        } finally {
            mon.unlock();
        }
    }

}
