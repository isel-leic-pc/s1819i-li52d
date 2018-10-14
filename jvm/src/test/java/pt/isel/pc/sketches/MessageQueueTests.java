package pt.isel.pc.sketches;

import org.junit.Test;
import pt.isel.pc.Helper;
import sketches.MessageQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class MessageQueueTests {

    private static class TestMessage {
        public AtomicInteger receiveCount = new AtomicInteger(0);
    }

    @Test
    public void test() throws InterruptedException {
        int nOfProducers = 10;
        int nOfConsumers = 20;
        int nOfReps = 100;
        Helper producers = new Helper();
        Helper consumers = new Helper();
        MessageQueue<TestMessage> q = new MessageQueue<>();
        List<List<TestMessage>> msgs = new ArrayList<List<TestMessage>>();
        CyclicBarrier barrier = new CyclicBarrier(nOfProducers);
        for(int i = 0 ; i<nOfProducers ; ++i) {
            int producerIx = i;
            List<TestMessage> myMessages = new ArrayList<>(nOfReps);
            msgs.add(myMessages);
            producers.createAndStart(() -> {
                barrier.await();
                for(int j = 0 ; j<nOfReps ; ++j) {
                    TestMessage msg = new TestMessage();
                    myMessages.add(msg);
                    q.put(msg, Long.MAX_VALUE);
                }
            });
        }
        for(int i = 0 ; i<nOfConsumers ; ++i) {
            consumers.createAndStart(() -> {
                while(true) {
                    Optional<TestMessage> message = q.get(Long.MAX_VALUE);
                    message.get().receiveCount.incrementAndGet();
                    Thread.sleep(100);
                }
            });
        }
        producers.join();
        Thread.sleep(1000);
        consumers.interruptAndJoin();
        for(List<TestMessage> list : msgs) {
            for(TestMessage msg : list) {
                assertEquals(1, msg.receiveCount.get());
            }
        }
    }
}
