package pt.isel.pc.sketches.nio;

import java.util.Arrays;
import java.util.List;

public class CompositeException extends Exception {
    private final List<Throwable> exceptions;

    public CompositeException(Throwable... exs) {
        this.exceptions = Arrays.asList(exs);
    }

    public List<Throwable> getExceptions() {
        return exceptions;
    }
}
