package pt.isel.pc.sketches.nio;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class AsynchronousSocketTests {

    private static final Logger log = LoggerFactory.getLogger(AsynchronousSocketTests.class);

    class EchoMachine {

        private final AsynchronousSocketChannel socket;

        private AtomicInteger state = new AtomicInteger(0);

        private ByteBuffer[] buffers = new ByteBuffer[]{
                ByteBuffer.allocateDirect(4 * 1024),
                ByteBuffer.allocateDirect(4 * 1024)
        };
        int readBufferIx = 0;
        int writeBufferIx = 1;

        int readResult = 0;

        private CompletionHandler<Integer,? super Object> readHandler = new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
                log.error("read completed with {}", result);
                readResult = result;
                if(state.decrementAndGet() == 0) {
                    completeCycle();
                }
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                log.error("read failed", exc);
            }
        };

        private CompletionHandler<Integer,? super Object> writeHandler = new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
                log.error("write completed with {}", result);
                if(state.decrementAndGet() == 0) {
                    completeCycle();
                }
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                log.error("write failed", exc);
            }
        };

        private void completeCycle() {
            if(readResult == -1) {
                log.info("done");
                try {
                    socket.close();
                } catch (IOException e) {
                    log.error("error on close, ignoring", e);
                }
                return;
            }
            buffers[readBufferIx].flip();
            buffers[writeBufferIx].clear();
            readBufferIx = (readBufferIx + 1) % 2;
            writeBufferIx = (writeBufferIx + 1) % 2;
            state.set(2);
            socket.read(buffers[readBufferIx], null, readHandler);
            socket.write(buffers[writeBufferIx], null, writeHandler);
        }

        public EchoMachine(AsynchronousSocketChannel socket) {
            this.socket = socket;
        }

        public void start() {
            state.set(1);
            socket.read(buffers[readBufferIx], null, readHandler);
        }

    }

    class ListenMachine implements CompletionHandler<AsynchronousSocketChannel, Object> {

        private final AsynchronousServerSocketChannel server;

        public ListenMachine(AsynchronousServerSocketChannel server) {
            this.server = server;
        }

        @Override
        public void completed(AsynchronousSocketChannel result, Object attachment) {
            log.info("socket accepted");
            server.accept(null, this);
            new EchoMachine(result).start();
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            log.error("error on listen", exc);
        }

        public void start() {
            server.accept(null, this);
        }
    }

    @Test
    public void first() throws IOException, InterruptedException {
        Semaphore end = new Semaphore(0);
        AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
        server.bind(new InetSocketAddress("127.0.0.1", 8081));
        ListenMachine listenMachine = new ListenMachine(server);
        listenMachine.start();
        end.acquire();
    }

}
