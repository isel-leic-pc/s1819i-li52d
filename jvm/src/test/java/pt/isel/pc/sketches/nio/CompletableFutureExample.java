package pt.isel.pc.sketches.nio;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

public class CompletableFutureExample {

    private static final Logger log = LoggerFactory.getLogger(CompletableFutureExample.class);


    private static CompletableFuture<Integer> read(AsynchronousFileChannel channel, ByteBuffer buffer, int position) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        channel.read(buffer, position, future, new CompletionHandler<Integer, CompletableFuture>() {
            @Override
            public void completed(Integer result, CompletableFuture attachment) {
                attachment.complete(result);
            }

            @Override
            public void failed(Throwable exc, CompletableFuture attachment) {
                attachment.completeExceptionally(exc);
            }
        });
        return future;
    }


    @Test
    public void example() throws IOException, InterruptedException {
        Semaphore sem = new Semaphore(0);
        AsynchronousFileChannel src = AsynchronousFileChannel.open(Paths.get("large.bin"));
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        CompletableFuture<Integer> f = read(src, buffer, 0);
        f.whenComplete((i, ex) -> {
            if (ex != null) {
                log.error("read completed with error", ex);
            } else {
                log.info("read completed with {} bytes", i);
            }
            sem.release();
        });
        sem.acquire();
    }
}
