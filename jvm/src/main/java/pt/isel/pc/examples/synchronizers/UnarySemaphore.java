package pt.isel.pc.examples.synchronizers;

public interface UnarySemaphore {
    boolean acquire(long timeout) throws InterruptedException;

    void release();
}
