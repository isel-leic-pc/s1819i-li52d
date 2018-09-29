package pt.isel.pc.examples.synchronizers;

public interface NarySemaphore {

    boolean tryAcquire(int requestUnits, long timeoutInMs)
            throws InterruptedException;

    void release(int releaseUnits);
}
