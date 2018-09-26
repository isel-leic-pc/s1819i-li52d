package sketches;

public interface NarySemaphore {

    boolean tryAcquire(int requestUnits, long timeoutInMs)
            throws InterruptedException;

    void release(int releaseUnits);
}
