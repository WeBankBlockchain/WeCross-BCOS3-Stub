package com.webank.wecross.stub.bcos3.protocol.response;

import org.fisco.bcos.sdk.v3.client.protocol.model.JsonTransactionResponse;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;

public class TransactionProof {

    private JsonTransactionResponse transAndProof;
    private TransactionReceipt receiptAndProof;

    public TransactionProof() {}

    public TransactionProof(
            JsonTransactionResponse transAndProof, TransactionReceipt receiptAndProof) {
        this.transAndProof = transAndProof;
        this.receiptAndProof = receiptAndProof;
    }

    public JsonTransactionResponse getTransAndProof() {
        return transAndProof;
    }

    public void setTransAndProof(JsonTransactionResponse transAndProof) {
        this.transAndProof = transAndProof;
    }

    public TransactionReceipt getReceiptAndProof() {
        return receiptAndProof;
    }

    public void setReceiptAndProof(TransactionReceipt receiptAndProof) {
        this.receiptAndProof = receiptAndProof;
    }

    @Override
    public String toString() {
        return "TransactionProof{"
                + "transAndProof="
                + transAndProof
                + ", receiptAndProof="
                + receiptAndProof
                + '}';
    }
}
