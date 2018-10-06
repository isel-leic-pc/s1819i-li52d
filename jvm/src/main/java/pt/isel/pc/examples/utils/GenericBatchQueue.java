package pt.isel.pc.examples.utils;

public class GenericBatchQueue<R> {

    private R request;
    private int nOfElements;

    public GenericBatchQueue(R request) {
        this.request = request;
        nOfElements = 0;
    }

    public void newBatch(R request) {
        this.request = request;
        nOfElements = 0;
    }

    public int getCount() {
        return nOfElements;
    }

    public R add() {
        nOfElements += 1;
        return request;
    }

    public R getCurrentRequest() {
        return request;
    }
}
