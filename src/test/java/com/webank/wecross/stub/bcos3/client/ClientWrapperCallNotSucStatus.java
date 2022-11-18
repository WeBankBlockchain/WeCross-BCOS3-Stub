package com.webank.wecross.stub.bcos3.client;

import com.webank.wecross.stub.bcos3.common.StatusCode;
import org.fisco.bcos.sdk.client.protocol.response.Call;

public class ClientWrapperCallNotSucStatus extends ClientWrapperImplMock {
    @Override
    public Call.CallOutput call(String accountAddress, String contractAddress, String data) {
        Call.CallOutput callOutput = new Call.CallOutput();
        callOutput.setCurrentBlockNumber("0x1111");
        callOutput.setStatus(StatusCode.RevertInstruction);
        callOutput.setOutput(data.substring(10));
        return callOutput;
    }
}
