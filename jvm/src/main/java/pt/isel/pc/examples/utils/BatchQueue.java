package pt.isel.pc.examples.utils;

public class BatchQueue {

    private int batchNumber = 0;
    private int nOfElemsInBatch = 0;

    public int addElementToBatch() {
        nOfElemsInBatch += 1;
        return batchNumber;
    }

    public void removeElementFromBatch(int batchNumber) {
        if(this.batchNumber != batchNumber || nOfElemsInBatch == 0) {
            throw new IllegalStateException();
        }
        nOfElemsInBatch -= 1;
    }

    public int completeBatchAndGetNumberOfElements() {
        batchNumber += 1;
        int res = nOfElemsInBatch;
        nOfElemsInBatch = 0;
        return res;
    }

    public boolean isBatchCompleted(int batchNumber) {
        return this.batchNumber != batchNumber;
    }

    public boolean isCurrentBatchEmpty() {
        return nOfElemsInBatch == 0;
    }

}
