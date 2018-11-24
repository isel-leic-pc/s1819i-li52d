package pt.isel.pc.examples.nio;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Semaphore;

public class AsyncHttpRequestExampleTests {

    private static final Logger log = LoggerFactory.getLogger(AsyncHttpRequestExampleTests.class);

    @Test
    public void test() throws IOException, InterruptedException {

        // NIO - New IO - IO não bloqueante.
        // NIO2 - New IO 2 - IO assíncrono baseado em Futures(*) e em Callbacks
        // (*) Futures *muito* limitados
        // Conceitos:
        // - Buffer (e.g. ByteBuffer).
        // - Channel - interface de comunicação
        // - CompletionHandler - callback chamado quando uma operação está concluída
        Semaphore done = new Semaphore(0);
        AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
        log.info("starting connect");
        channel.connect(new InetSocketAddress("httpbin2.org", 80), null,
                new CompletionHandler<Void, Object>() {
                    @Override
                    public void completed(Void result, Object attachment) {
                        // chamado em caso de sucesso
                        log.info("connect completed with success");
                        done.release();
                    }

                    @Override
                    public void failed(Throwable exc, Object attachment) {
                        // chamado em caso de falha
                        log.error("connect completed with error", exc);
                        done.release();
                    }
                });
        done.acquire();
        log.info("ending");

    }

}
