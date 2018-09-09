package pt.isel.pc.examples.intro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/*
 * Examples with explicit thread creation.
 */


public class CreatingThreadsExamples {

    public static Logger log = LoggerFactory.getLogger(CreatingThreadsExamples.class);

    public static void main(String[] args) throws InterruptedException {
        //createThreadsUsingSubclasses();
        createThreadsUsingLambdas();
    }

    private static void createThreadsUsingSubclasses() throws InterruptedException {
        List<Thread> ths = new LinkedList<>();
        for(int i = 0 ; i<100 ; ++i) {
            Thread th = new MyThread();
            ths.add(th);
            th.start();
        }
        for(Thread th : ths) {
            th.join();
        }
    }

    private static class MyThread extends Thread {

        @Override
        public void run() {
            log.info("Thread {} just started", this.getName());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // ignoring it
            }
            log.info("Thread {} about to end", this.getName());
        }
    }

    private static void createThreadsUsingLambdas() throws InterruptedException {
        List<Thread> ths = new LinkedList<>();
        for(int i = 0 ; i<100 ; ++i) {
            Thread th = new Thread(() -> {
                Thread thisThread = Thread.currentThread();
                log.info("Thread {} just started", thisThread.getName());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // ignoring it
                }
                log.info("Thread {} about to end", thisThread.getName());
            });
            // notice that an explicit start is required to start the thread
            th.start();
            ths.add(th);
        }
        for(Thread th : ths) {
            th.join();
        }
    }
}
