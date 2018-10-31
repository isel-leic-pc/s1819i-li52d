package sketches;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class SimpleStack<T> {

    public static class Node<T> {
        public final T value;
        public Node<T> next;

        public Node(T value) {
            this.value = value;
        }
    }

    private final AtomicReference<Node<T>> top;

    public SimpleStack(){

        top = new AtomicReference<>();
    }

    public SimpleStack(T t) {
        top = new AtomicReference<>(new Node<>(t));
    }

    public void push(T t) {
        Node<T> mynode = new Node<>(t);
        while(true) {
            Node<T> observedTop = top.get();
            mynode.next = observedTop;
            if(top.compareAndSet(observedTop, mynode)) {
                return;
            }
        }

    }

    // non-blocking
    public Optional<T> pop() {
        while(true) {
            Node<T> observedTop = top.get();
            if (observedTop == null) {
                return Optional.empty();
            }
            if(top.compareAndSet(observedTop, observedTop.next)) {
                return Optional.of(observedTop.value);
            }
        }
    }

}
