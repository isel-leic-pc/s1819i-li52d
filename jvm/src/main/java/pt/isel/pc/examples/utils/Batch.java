package pt.isel.pc.examples.utils;

import java.util.concurrent.locks.Condition;

public class Batch {
    private int nOfElements;
    private final Condition cond;
    private boolean isDone;

    public Batch(Condition cond) {
        this.cond = cond;
    }

    public Batch add() {
        nOfElements += 1;
        return this;
    }

    public void cancel() {
        nOfElements -= 1;
    }

    public int complete() {
        if (nOfElements != 0) {
            cond.signalAll();
        }
        isDone = true;
        return nOfElements;
    }

    public boolean isDone() {
        return isDone;
    }

    public Condition getCondition() {
        return cond;
    }

    public boolean hasAny() {
        return nOfElements != 0;
    }
}
