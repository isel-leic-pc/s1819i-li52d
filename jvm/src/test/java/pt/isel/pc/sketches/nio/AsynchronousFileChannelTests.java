package pt.isel.pc.sketches.nio;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

public class AsynchronousFileChannelTests {

    private static final Logger log = LoggerFactory.getLogger(AsynchronousFileChannelTests.class);

    private static class CompletionExecutor {
        private final Executor executor;

        public CompletionExecutor(Executor executor) {

            this.executor = executor;
        }

        public <V, A> void run(Callable<V> callable, A attachment, CompletionHandler<V, A> handler) {
            executor.execute(() -> {
                try {
                    handler.completed(callable.call(), attachment);
                } catch (Exception e) {
                    handler.failed(e, attachment);
                }
            });
        }

        public <V, A> void complete(V result, A attachment, CompletionHandler<V, A> handler) {
            executor.execute(() -> {
                handler.completed(result, attachment);
            });
        }

        public <V, A> void fail(Throwable th, A attachment, CompletionHandler<V, A> handler) {
            executor.execute(() -> {
                handler.failed(th, attachment);
            });
        }
    }

    private static class Copier<A> {

        private final AsynchronousFileChannel src;
        private final AsynchronousFileChannel dest;
        private final CompletionHandler<Boolean, A> handler;
        private final A attachment;
        private final CompletionExecutor completionExecutor = new CompletionExecutor(Executors.newSingleThreadExecutor());

        private AtomicInteger state = new AtomicInteger(0);

        private ByteBuffer[] buffers = new ByteBuffer[]{
                ByteBuffer.allocateDirect(4 * 1024),
                ByteBuffer.allocateDirect(4 * 1024)
        };
        int readBufferIx = 0;
        int writeBufferIx = 1;
        private long readPosition = 0;
        private long writePosition = 0;

        private Throwable currentThrowable;
        private int readResult;
        private int writeResult;
        private boolean flushing = false;

        public Copier(
                AsynchronousFileChannel src,
                AsynchronousFileChannel dest,
                A attachment,
                CompletionHandler<Boolean, A> handler
                ){

            this.src = src;
            this.dest = dest;
            this.handler = handler;
            this.attachment = attachment;
        }

        public void start() {
            state.set(1);
            src.read(buffers[readBufferIx], readPosition, null, readHandler);
        }

        private void completeCycle() {
            log.info("completing cycle");
            if(currentThrowable != null) {
                completionExecutor.fail(currentThrowable, attachment, handler);
                return;
            }
            if(flushing) {
                completionExecutor.complete(true, attachment, handler);
                return;
            }
            buffers[readBufferIx].flip();
            buffers[writeBufferIx].clear();
            readBufferIx = (readBufferIx + 1) % 2;
            writeBufferIx = (writeBufferIx + 1) % 2;
            readPosition += readResult;
            writePosition += writeResult;
            if(readResult == -1){
                completionExecutor.complete(true, attachment, handler);
                return;
            }
            state.set(2);
            src.read(buffers[readBufferIx], readPosition, null, readHandler);
            dest.write(buffers[writeBufferIx], writePosition, null, writeHandler);
        }

        private final CompletionHandler<Integer, Object> readHandler = new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
                readResult = result;
                if(state.decrementAndGet() == 0) {
                    completeCycle();
                }
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                currentThrowable = exc;
                if(state.decrementAndGet() == 0) {
                    completeCycle();
                }
            }
        };

        private final CompletionHandler<Integer, Object> writeHandler = new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
                writeResult = result;
                if(state.decrementAndGet() == 0) {
                    completeCycle();
                }
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                currentThrowable = exc;
                if(state.decrementAndGet() == 0) {
                    completeCycle();
                }
            }
        };
    }

    @Test
    public void testCopy() throws IOException, InterruptedException {
        Semaphore s = new Semaphore(0);

        AsynchronousFileChannel src = AsynchronousFileChannel.open(Paths.get("large.bin"));
        AsynchronousFileChannel dest = AsynchronousFileChannel.open(Paths.get("dest.bin"),
                WRITE, CREATE);
        Copier<Object> copier = new Copier<>(src, dest, null, new CompletionHandler<Boolean, Object>() {
            @Override
            public void completed(Boolean result, Object attachment) {
                log.info("result = {}", result);
                s.release();
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                log.error("error occurred", exc);
                s.release();
            }
        });
        copier.start();
        s.acquire();
    }

    private static class Hasher<A> {

        private final AsynchronousFileChannel channel;
        private final A attachment;
        private final CompletionHandler<byte[], A> handler;
        private final MessageDigest digest;
        private final CompletionExecutor completionExecutor;
        private ByteBuffer[] buffers = new ByteBuffer[]{
                ByteBuffer.allocateDirect(4 * 1024),
                ByteBuffer.allocateDirect(4 * 1024)
        };
        int buffersIx = 0;
        private long position = 0;

        private AtomicInteger state = new AtomicInteger(0);
        private volatile int currentResult;
        private volatile Throwable currentThrowable;

        public Hasher(AsynchronousFileChannel channel, A attachment, CompletionHandler<byte[], A> handler) throws NoSuchAlgorithmException {

            this.channel = channel;
            this.attachment = attachment;
            this.handler = handler;
            this.digest = MessageDigest.getInstance("sha1");
            this.completionExecutor = new CompletionExecutor(Executors.newSingleThreadExecutor());
        }

        private final CompletionHandler<Integer, Object> readHandler = new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
                log.info("read completed with result = {}", result);
                currentResult = result;
                if (state.decrementAndGet() == 0) {
                    completeIteration();
                }
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                currentThrowable = exc;
                if (state.decrementAndGet() == 0) {
                    completeIteration();
                }
            }
        };

        private final CompletionHandler<Integer, Object> hashHandler = new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
                log.info("hash completed");
                if (state.decrementAndGet() == 0) {
                    completeIteration();
                }
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                currentThrowable = exc;
                if (state.decrementAndGet() == 0) {
                    completeIteration();
                }
            }
        };

        private void completeIteration() {
            log.info("completed iteration with currentResult = {} and buffersIx = {}", currentResult, buffersIx);
            if(currentThrowable != null) {
                completionExecutor.fail(currentThrowable, attachment, handler);
                return;
            }
            state.set(2);
            if (currentResult != -1) {
                buffers[buffersIx].flip();
                ByteBuffer bufferToHash = buffers[buffersIx];
                buffersIx = (buffersIx + 1) % 2;
                buffers[buffersIx].clear();
                position += currentResult;
                completionExecutor.run(() -> {
                    digest.update(bufferToHash);
                    return 1;
                }, null, hashHandler);
                channel.read(buffers[buffersIx], position, null, readHandler);
            } else {
                completionExecutor.run(() -> {
                    return digest.digest();
                }, null, handler);
            }
        }

        public void start() {
            state.set(1);
            channel.read(buffers[0], position, null, readHandler);
        }

    }

    @Test
    public void testHash() throws IOException, InterruptedException, NoSuchAlgorithmException {
        Semaphore s = new Semaphore(0);
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(Paths.get("large.bin"));
        Hasher<Object> hasher = new Hasher<>(channel, null, new CompletionHandler<byte[], Object>() {
            @Override
            public void completed(byte[] result, Object attachment) {
                log.info("result = {}", javax.xml.bind.DatatypeConverter.printHexBinary(result));
                s.release();
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                log.error("error occurred", exc);
                s.release();
            }
        });
        hasher.start();
        s.acquire();
    }

    private static class Reader<A> implements CompletionHandler<Integer, A> {

        private final AsynchronousFileChannel channel;
        private final A attachment;
        private final CompletionHandler<Long, A> handler;
        private long position = 0;
        private long size = 0;
        private ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 1024);
        private Throwable exc;

        public Reader(AsynchronousFileChannel channel, A attachment, CompletionHandler<Long, A> handler) {

            this.channel = channel;
            this.attachment = attachment;
            this.handler = handler;
        }

        public void start() {
            channel.read(buffer, position, null, this);
        }

        @Override
        public void completed(Integer result, Object a) {
            log.info("result = {}, old size = {}", result, size);
            if (result != -1) {
                size += result;
                position += result;
                buffer.clear();
                channel.read(buffer, position, null, this);
                return;
            }
            log.info("Done, calling handler");
            handler.completed(size, attachment);
        }

        @Override
        public void failed(Throwable exc, Object a) {
            log.error("Error occurred, calling handler", exc);
            handler.failed(exc, attachment);
        }
    }

    @Test
    public void testRead() throws IOException, InterruptedException {
        Semaphore s = new Semaphore(0);
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(Paths.get("large.bin"));
        Reader<Object> reader = new Reader<>(channel, null, new CompletionHandler<Long, Object>() {
            @Override
            public void completed(Long result, Object attachment) {
                log.info("Done, size is {}", result);
                s.release();
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                log.info("Done with exception {}", exc.getMessage());
                s.release();
            }
        });
        reader.start();
        s.acquire();
    }

}
