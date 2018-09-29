package sketches;

import pt.isel.pc.examples.utils.NodeLinkedList;
import pt.isel.pc.examples.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ManualResetEvent {

    private static class State {
        public boolean canLeave = false;
    }

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private boolean state = false;
    NodeLinkedList<State> q = new NodeLinkedList<>();

    public void set() {
        try{
            lock.lock();
            state = true;
            while(!q.isEmpty()){
                q.pull().value.canLeave = true;
            }
            condition.signalAll();
        }finally{
            lock.unlock();
        }
    }

    public void reset() {
        try{
            lock.lock();
            state = false;
        }finally{
            lock.unlock();
        }
    }

    public boolean await(long timeout) throws InterruptedException {
        try{
            lock.lock();

            // happy path
            if(state == true) {
                return true;
            }
            if(Timeouts.noWait(timeout)) {
                return false;
            }
            long limit = Timeouts.start(timeout);
            long remainingInMs = Timeouts.remaining(limit);
            NodeLinkedList.Node<State> node = q.push(new State());
            while(true) {
                condition.await(remainingInMs, TimeUnit.MILLISECONDS);
                if(node.value.canLeave == true) {
                    return true;
                }
                remainingInMs = Timeouts.remaining(limit);
                if(Timeouts.isTimeout(remainingInMs)) {
                    return false;
                }
            }
        }finally{
            lock.unlock();
        }
    }

}
