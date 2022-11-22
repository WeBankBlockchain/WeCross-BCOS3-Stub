package com.webank.wecross.stub.bcos3.client;


import org.fisco.bcos.sdk.v3.client.protocol.model.JsonTransactionResponse;
import org.fisco.bcos.sdk.v3.client.protocol.response.BcosBlock;
import org.fisco.bcos.sdk.v3.client.protocol.response.BcosBlockHeader;
import org.fisco.bcos.sdk.v3.client.protocol.response.Call;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.model.callback.TransactionCallback;

import java.io.IOException;
import java.math.BigInteger;

public class ClientWrapperWithExceptionMock extends AbstractClientWrapper {

    public ClientWrapperWithExceptionMock() {
        super(null);
    }

    @Override
    public BcosBlock.Block getBlockByNumber(long blockNumber) throws IOException {
        throw new IOException(" IOException");
    }

    public BcosBlockHeader.BlockHeader getBlockHeaderByNumber(long blockNumber) throws IOException {
        throw new IOException(" IOException");
    }

    @Override
    public BigInteger getBlockNumber() throws IOException {
        throw new IOException(" IOException");
    }

    @Override
    public void sendTransaction(String signedTransactionData, TransactionCallback callback)
            throws IOException {
        throw new IOException(" IOException");
    }

    @Override
    public TransactionReceipt getTransactionReceiptByHashWithProof(
            String transactionHash) throws IOException {
        throw new IOException(" IOException");
    }

    @Override
    public JsonTransactionResponse getTransactionByHashWithProof(
            String transactionHash) throws IOException {
        throw new IOException(" IOException");
    }

    @Override
    public Call.CallOutput call(String accountAddress, String contractAddress, byte[] data)
            throws IOException {
        throw new IOException(" IOException");
    }
}
