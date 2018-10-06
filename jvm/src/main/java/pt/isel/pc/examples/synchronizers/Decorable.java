package pt.isel.pc.examples.synchronizers;

public interface Decorable {

    @FunctionalInterface
    public interface ERunnable {
        void run() throws InterruptedException;
    }

    void decorate(ERunnable runnable) throws InterruptedException;

}