package pt.isel.pc.examples.synchronizers;

public interface ReaderWriterLock extends Decorable {

    void startRead() throws InterruptedException;
    void startWrite() throws InterruptedException;
    void endRead();
    void endWrite();
}
