package pt.isel.pc.sketches.nio;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Paths;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

public class FileCopyExample {

    private static final Logger log = LoggerFactory.getLogger(FileCopyExample.class);

    private static class FileCopier<A> {

        // shared for all FileCopier instances
        private static Executor threadPool = Executors.newFixedThreadPool(2);

        private final AsynchronousFileChannel src;
        private final AsynchronousFileChannel dest;
        private final A attachment;
        private final CompletionHandler<Integer, A> handler;
        private final ByteBuffer[] buffers = new ByteBuffer[] {
                ByteBuffer.allocate(4*1024*1024),
                ByteBuffer.allocate(4*1024*1024)
        };

        private int readBufferIx = 0;
        private int writeBufferIx = 1;

        private int readPosition = 0;
        private int writePosition = 0;

        private int readResult;
        private int writeResult;
        private Throwable readException;
        private Throwable writeException;

        private final AtomicInteger pendingOperations = new AtomicInteger();

        public FileCopier(
                AsynchronousFileChannel src, AsynchronousFileChannel dest,
                A attachment, CompletionHandler<Integer, A> handler) {
            this.src = src;
            this.dest = dest;
            this.attachment = attachment;
            this.handler = handler;
        }

        public void start() {
            pendingOperations.set(1);
            src.read(buffers[readBufferIx], readPosition, null, readHandler);
        }

        private void completeReadWriteCycle() {
            // was there an error?
            if(readException != null || writeException != null) {
                CompositeException ex;
                if(readException != null && writeException != null) {
                    ex = new CompositeException(readException, writeException);
                } else if(readException != null) {
                    ex = new CompositeException(readException);
                } else {
                    ex = new CompositeException(writeException);
                }
                CompositeException finalEx = ex;
                handleFailed(finalEx);
                return;
            }
            // was it the end?
            if(readResult == -1) {
                handleCompleted();
                return;
            }
            readPosition += readResult;
            writePosition += writeResult;
            buffers[readBufferIx].flip();
            buffers[writeBufferIx].clear();
            readBufferIx = (readBufferIx + 1) % 2;
            writeBufferIx = (writeBufferIx + 1) % 2;
            pendingOperations.set(2);
            log.info("starting next cycle with readPosition = {}", readPosition);

            try {
                src.read(buffers[readBufferIx], readPosition, null, readHandler);
            }catch(Throwable ex) {
                handleFailed(ex);
                return;
            }
            try {
                dest.write(buffers[writeBufferIx], writePosition, null, writeHandler);
            }catch(Throwable ex) {
                tryCancelOperation(src);
                writeHandler.failed(ex, null);
            }
        }

        // Always call external completion handler asynchronously
        // i.e. via an executor
        private void handleFailed(Throwable ex) {
            threadPool.execute(() -> {
                handler.failed(ex, attachment);
            });
        }

        private void handleCompleted() {
            threadPool.execute(() -> {
                handler.completed(readPosition, attachment);
            });
        }

        private CompletionHandler<Integer, Object> readHandler = new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
                log.info("read step completed");
                readResult = result;
                if (pendingOperations.decrementAndGet() == 0) {
                    completeReadWriteCycle();
                }
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                log.info("read step failed with '{}'", exc.getClass().getName());
                tryCancelOperation(dest);
                readException = exc;
                if (pendingOperations.decrementAndGet() == 0) {
                    completeReadWriteCycle();
                }
            }
        };

        private CompletionHandler<Integer, Object> writeHandler = new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
                log.info("write step completed");
                writeResult = result;
                if(pendingOperations.decrementAndGet() == 0) {
                    completeReadWriteCycle();
                }
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                log.info("write step failed with '{}'", exc.getMessage());
                tryCancelOperation(src);
                writeException = exc;
                if(pendingOperations.decrementAndGet() == 0) {
                    completeReadWriteCycle();
                }
            }
        };

        private void tryCancelOperation(AsynchronousFileChannel channel) {
            try {
                channel.close();
            } catch (IOException e) {
                log.error("Error while closing channel", e);
                // ignoring since this is a "best try effort"
            }
        }
    }

    @Test
    public void copyFiles() throws IOException, InterruptedException {
        Semaphore done = new Semaphore(0);
        AsynchronousFileChannel src = AsynchronousFileChannel.open(Paths.get("large.bin"));
        AsynchronousFileChannel dest = AsynchronousFileChannel.open(Paths.get("dest.bin"), WRITE, CREATE);
        FileCopier<Object> copier = new FileCopier<>(src, dest, null, new CompletionHandler<Integer, Object>() {

            @Override
            public void completed(Integer result, Object attachment) {
                log.info("{} bytes were copied", result);
                done.release();
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                log.error("error ocurred", exc);
                done.release();
            }
        });
        copier.start();
        done.acquire();
    }
}
