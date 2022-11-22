package com.webank.wecross.stub.bcos3;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.webank.wecross.stub.Block;
import com.webank.wecross.stub.BlockHeader;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.bcos3.client.AbstractClientWrapper;
import com.webank.wecross.stub.bcos3.client.ClientWrapperCallNotSucStatus;
import com.webank.wecross.stub.bcos3.client.ClientWrapperImplMock;
import com.webank.wecross.stub.bcos3.client.ClientWrapperWithExceptionMock;
import com.webank.wecross.stub.bcos3.client.ClientWrapperWithNullMock;
import com.webank.wecross.stub.bcos3.common.BCOSConstant;
import com.webank.wecross.stub.bcos3.common.BCOSRequestType;
import com.webank.wecross.stub.bcos3.common.BCOSStatusCode;
import com.webank.wecross.stub.bcos3.common.ObjectMapperFactory;
import com.webank.wecross.stub.bcos3.common.StatusCode;
import com.webank.wecross.stub.bcos3.config.BCOSStubConfig;
import com.webank.wecross.stub.bcos3.config.BCOSStubConfigParser;
import com.webank.wecross.stub.bcos3.contract.BlockUtility;
import com.webank.wecross.stub.bcos3.contract.FunctionUtility;
import com.webank.wecross.stub.bcos3.contract.SignTransaction;
import com.webank.wecross.stub.bcos3.protocol.request.TransactionParams;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.fisco.bcos.sdk.v3.client.protocol.response.BcosBlock;
import org.fisco.bcos.sdk.v3.client.protocol.response.Call;
import org.fisco.bcos.sdk.v3.codec.datatypes.Function;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.transaction.codec.encode.TransactionEncoderService;
import org.fisco.bcos.sdk.v3.utils.Numeric;
import org.junit.Test;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

public class BCOSConnectionTest {
    @Test
    public void toBlockHeaderTest() throws IOException {
        String blockJson =
                "{\"number\":331,\"hash\":\"0x6db416c8ac6b1fe7ed08771de419b71c084ee5969029346806324601f2e3f0d0\",\"parentHash\":\"0xed0ef6826277efbc9601dedc1b6ea20067eed219e415e1038f111155b8fc1e24\",\"nonce\":0,\"sha3Uncles\":null,\"logsBloom\":\"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000\",\"transactionsRoot\":\"0x07009a9d655cee91e95dcd1c53d5917a58f80e6e6ac689bae24bd911d75c471c\",\"stateRoot\":\"0xce8a92c9311e9e0b77842c86adf8fcf91cbab8fb5daefc85b21f501ca8b1f682\",\"receiptsRoot\":\"0x2a4433b7611c4b1fae16b873ced1dec9a65b82416e448f58fded002c05a10082\",\"author\":null,\"sealer\":\"0x1\",\"mixHash\":null,\"extraData\":[],\"gasLimit\":0,\"gasUsed\":0,\"timestamp\":1584081463141,\"transactions\":[{}],\"uncles\":null,\"sealerList\":[\"7f6b1fc98c6bc8dbde4afe62bf1322a4f10ff29528f1e6bb0e57590aa81c31bfe57510787c5adf3fb90fb4239d5483c0d805874451aeb7e76c6c15e1b2123165\",\"9b04ba34f30452a43e7868e1b918c380f1d3d3bdc98d752d1dc30155e6a3dd9da6e530a4351eb4eab42f8703a3922233b830f2678c14179e3ac0f9e5bef8c954\",\"f4c43730a29511e66e9eddbee7024a65d8a8b3b886e6f652785faefb979676f04bd9671529aef9147c86edf58df0482b4e5b293006a179b14039484c6d20a18e\"],\"numberRaw\":\"0x14b\",\"nonceRaw\":null,\"gasLimitRaw\":\"0x0\",\"gasUsedRaw\":\"0x0\",\"timestampRaw\":\"0x170d29ce765\"}";
        ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        BcosBlock.Block block = objectMapper.readValue(blockJson, BcosBlock.Block.class);

        BlockHeader blockHeader = BlockUtility.convertToBlockHeader(block);

        assertEquals(blockHeader.getHash(), block.getHash());
        assertEquals(blockHeader.getPrevHash(), block.getParentHash());
        assertEquals(blockHeader.getReceiptRoot(), block.getReceiptsRoot());
        assertEquals(blockHeader.getStateRoot(), block.getStateRoot());
        assertEquals(blockHeader.getNumber(), block.getNumber().intValue());
        assertEquals(blockHeader.getTransactionRoot(), block.getTransactionsRoot());
    }

    @Test
    public void resourceInfoListTest() throws IOException {
        BCOSStubConfigParser bcosStubConfigParser =
                new BCOSStubConfigParser("./", "stub-sample-ut.toml");
        BCOSStubConfig bcosStubConfig = bcosStubConfigParser.loadConfig();

        List<ResourceInfo> resourceInfoList = bcosStubConfig.convertToResourceInfos();

        assertEquals(resourceInfoList.size(), bcosStubConfig.getResources().size());
        assertFalse(resourceInfoList.isEmpty());
        for (int i = 0; i < resourceInfoList.size(); i++) {
            ResourceInfo resourceInfo = resourceInfoList.get(i);
            assertEquals(resourceInfo.getStubType(), bcosStubConfig.getType());
            assertEquals(resourceInfo.getProperties().get(BCOSConstant.BCOS_CHAIN_ID), 123);
            assertEquals(resourceInfo.getProperties().get(BCOSConstant.BCOS_GROUP_ID), 111);
        }
    }

    @Test
    public void handleUnknownTypeTest() {
        AbstractClientWrapper clientWrapper = new ClientWrapperImplMock();
        BCOSConnection connection =
                new BCOSConnection(
                        clientWrapper,
                        new ScheduledThreadPoolExecutor(
                                1, new CustomizableThreadFactory(this.getClass().getName())));
        Request request = new Request();
        request.setType(2000);
        connection.asyncSend(
                request,
                response ->
                        assertEquals(
                                response.getErrorCode(), BCOSStatusCode.UnrecognizedRequestType));
    }

    @Test
    public void handleGetBlockNumberTest() {
        AbstractClientWrapper clientWrapper = new ClientWrapperImplMock();
        BCOSConnection connection =
                new BCOSConnection(
                        clientWrapper,
                        new ScheduledThreadPoolExecutor(
                                1, new CustomizableThreadFactory(this.getClass().getName())));
        Request request = new Request();
        request.setType(BCOSRequestType.GET_BLOCK_NUMBER);
        connection.asyncSend(
                request,
                response -> {
                    BigInteger blockNumber = new BigInteger(response.getData());
                    assertEquals(response.getErrorCode(), BCOSStatusCode.Success);
                    assertEquals(blockNumber.longValue(), 11111);
                });
    }

    @Test
    public void handleFailedGetBlockNumberTest() {
        AbstractClientWrapper clientWrapper = new ClientWrapperWithExceptionMock();
        BCOSConnection connection =
                new BCOSConnection(
                        clientWrapper,
                        new ScheduledThreadPoolExecutor(
                                1, new CustomizableThreadFactory(this.getClass().getName())));
        Request request = new Request();
        request.setType(BCOSRequestType.GET_BLOCK_NUMBER);
        connection.asyncSend(
                request,
                response ->
                        assertEquals(
                                response.getErrorCode(),
                                BCOSStatusCode.HandleGetBlockNumberFailed));
    }

    @Test
    public void handleGetBlockTest() {

        AbstractClientWrapper clientWrapper = new ClientWrapperImplMock();
        BCOSConnection connection =
                new BCOSConnection(
                        clientWrapper,
                        new ScheduledThreadPoolExecutor(
                                1, new CustomizableThreadFactory(this.getClass().getName())));
        Request request = new Request();
        request.setType(BCOSRequestType.GET_BLOCK_BY_NUMBER);

        request.setData(BigInteger.valueOf(11111).toByteArray());

        connection.asyncSend(
                request,
                response -> {
                    assertTrue(Objects.nonNull(connection.getClientWrapper()));
                    assertEquals(response.getErrorCode(), 0);

                    Block block = null;
                    try {
                        block = BlockUtility.convertToBlock(response.getData(), false);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    assertTrue(!block.transactionsHashes.isEmpty());

                    BlockHeader blockHeader = block.getBlockHeader();
                    assertEquals(
                            blockHeader.getHash(),
                            "0x99576e7567d258bd6426ddaf953ec0c953778b2f09a078423103c6555aa4362d");
                    assertEquals(
                            blockHeader.getPrevHash(),
                            "0x64ba7bf5c6b5a83854774475bf8511d5e9bb38d8a962a859b52aa9c9fba0c685");
                    assertEquals(
                            blockHeader.getReceiptRoot(),
                            "0x049389563053748a0fd2b256260b9e8c76a427b543bee18f3a221d80d1553da8");
                    assertEquals(
                            blockHeader.getStateRoot(),
                            "0xce8a92c9311e9e0b77842c86adf8fcf91cbab8fb5daefc85b21f501ca8b1f682");
                    assertEquals(
                            blockHeader.getTransactionRoot(),
                            "0xb563f70188512a085b5607cac0c35480336a566de736c83410a062c9acc785ad");
                });
    }

    @Test
    public void handleFailedGetBlockTest() {
        AbstractClientWrapper clientWrapper = new ClientWrapperWithExceptionMock();
        BCOSConnection connection =
                new BCOSConnection(
                        clientWrapper,
                        new ScheduledThreadPoolExecutor(
                                1, new CustomizableThreadFactory(this.getClass().getName())));
        Request request = new Request();
        request.setType(BCOSRequestType.GET_BLOCK_BY_NUMBER);

        request.setData(BigInteger.valueOf(11111).toByteArray());
        connection.asyncSend(
                request,
                response -> {
                    assertTrue(Objects.nonNull(connection.getClientWrapper()));
                    assertEquals(response.getErrorCode(), BCOSStatusCode.HandleGetBlockFailed);
                });
    }

    @Test
    public void handleCallTest() throws IOException {

        AbstractClientWrapper clientWrapper = new ClientWrapperImplMock();
        BCOSConnection connection =
                new BCOSConnection(
                        clientWrapper,
                        new ScheduledThreadPoolExecutor(
                                1, new CustomizableThreadFactory(this.getClass().getName())));

        String address = "0x6db416c8ac6b1fe7ed08771de419b71c084ee5969029346806324601f2e3f0d0";
        String funName = "funcName";
        String[] params = new String[] {"abc", "def", "hig"};
        Function function = FunctionUtility.newDefaultFunction(funName, params);

        FunctionEncoder functionEncoder = new FunctionEncoder(clientWrapper.getCryptoSuite());
        String abi = functionEncoder.encode(function);

        TransactionRequest transactionRequest = new TransactionRequest(funName, params);
        TransactionParams transactionParams =
                new TransactionParams(transactionRequest, abi, TransactionParams.SUB_TYPE.CALL);
        transactionParams.setFrom(address);
        transactionParams.setTo(address);

        Request request = new Request();
        request.setType(BCOSRequestType.CALL);
        request.setData(ObjectMapperFactory.getObjectMapper().writeValueAsBytes(transactionParams));
        connection.asyncSend(
                request,
                response -> {
                    assertEquals(response.getErrorCode(), BCOSStatusCode.Success);

                    Call.CallOutput callOutput = null;
                    try {
                        callOutput =
                                ObjectMapperFactory.getObjectMapper()
                                        .readValue(response.getData(), Call.CallOutput.class);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    assertEquals(callOutput.getStatus(), StatusCode.Success);

                    String data = callOutput.getOutput();

                    String[] strings = FunctionUtility.decodeDefaultOutput(data);
                    assertEquals(strings.length, params.length);

                    for (int i = 0; i < params.length; i++) {
                        assertEquals(strings[i], params[i]);
                    }
                });
    }

    @Test
    public void handleFailedCallTest() throws IOException {

        AbstractClientWrapper clientWrapper = new ClientWrapperWithExceptionMock();
        BCOSConnection connection =
                new BCOSConnection(
                        clientWrapper,
                        new ScheduledThreadPoolExecutor(
                                1, new CustomizableThreadFactory(this.getClass().getName())));

        String address = "0x6db416c8ac6b1fe7ed08771de419b71c084ee5969029346806324601f2e3f0d0";
        String funName = "funcName";
        String[] params = new String[] {"abc", "def", "hig"};
        Function function = FunctionUtility.newDefaultFunction(funName, params);

        FunctionEncoder functionEncoder = new FunctionEncoder(clientWrapper.getCryptoSuite());
        String abi = functionEncoder.encode(function);

        TransactionRequest transactionRequest = new TransactionRequest(funName, params);
        TransactionParams transactionParams =
                new TransactionParams(transactionRequest, abi, TransactionParams.SUB_TYPE.CALL);
        transactionParams.setTo(address);
        transactionParams.setFrom(address);

        Request request = new Request();
        request.setType(BCOSRequestType.CALL);
        request.setData(ObjectMapperFactory.getObjectMapper().writeValueAsBytes(transactionParams));
        connection.asyncSend(
                request,
                response ->
                        assertEquals(
                                response.getErrorCode(), BCOSStatusCode.HandleCallRequestFailed));
    }

    @Test
    public void handleFailedCallTest0() throws IOException {

        AbstractClientWrapper clientWrapper = new ClientWrapperCallNotSucStatus();
        BCOSConnection connection =
                new BCOSConnection(
                        clientWrapper,
                        new ScheduledThreadPoolExecutor(
                                1, new CustomizableThreadFactory(this.getClass().getName())));

        String address = "0x6db416c8ac6b1fe7ed08771de419b71c084ee5969029346806324601f2e3f0d0";
        String funName = "funcName";
        String[] params = new String[] {"abc", "def", "hig"};
        Function function = FunctionUtility.newDefaultFunction(funName, params);

        FunctionEncoder functionEncoder = new FunctionEncoder(clientWrapper.getCryptoSuite());
        String abi = functionEncoder.encode(function);

        TransactionRequest transactionRequest = new TransactionRequest(funName, params);
        TransactionParams transactionParams =
                new TransactionParams(transactionRequest, abi, TransactionParams.SUB_TYPE.CALL);
        transactionParams.setTo(address);
        transactionParams.setFrom(address);

        Request request = new Request();
        request.setType(BCOSRequestType.CALL);
        request.setData(ObjectMapperFactory.getObjectMapper().writeValueAsBytes(transactionParams));
        connection.asyncSend(
                request, response -> assertEquals(response.getErrorCode(), BCOSStatusCode.Success));
    }

    @Test
    public void handleSendTransactionTest() throws IOException {

        AbstractClientWrapper clientWrapper = new ClientWrapperImplMock();
        CryptoSuite cryptoSuite = clientWrapper.getCryptoSuite();
        BCOSConnection connection =
                new BCOSConnection(
                        clientWrapper,
                        new ScheduledThreadPoolExecutor(
                                1, new CustomizableThreadFactory(this.getClass().getName())));
        Request request = new Request();
        request.setType(BCOSRequestType.SEND_TRANSACTION);

        String address = "0x6db416c8ac6b1fe7ed08771de419b71c084ee5969029346806324601f2e3f0d0";
        String funName = "funcName";
        String[] params = new String[] {"abc", "def", "hig"};

        Function function = FunctionUtility.newDefaultFunction(funName, params);

        FunctionEncoder functionEncoder = new FunctionEncoder(cryptoSuite);
        String abi = functionEncoder.encode(function);

        RawTransaction rawTransaction =
                SignTransaction.buildTransaction(
                        address,
                        BigInteger.valueOf(1),
                        BigInteger.valueOf(1),
                        BigInteger.valueOf(1),
                        abi);
        TransactionEncoderService transactionEncoderService =
                new TransactionEncoderService(cryptoSuite);
        CryptoKeyPair credentials = cryptoSuite.getCryptoKeyPair();
        String sign = transactionEncoderService.encodeAndSign(rawTransaction, credentials);

        TransactionRequest transactionRequest = new TransactionRequest();
        transactionRequest.setMethod(funName);
        transactionRequest.setArgs(params);
        TransactionParams transaction1 =
                new TransactionParams(transactionRequest, sign, TransactionParams.SUB_TYPE.SEND_TX);

        request.setData(ObjectMapperFactory.getObjectMapper().writeValueAsBytes(transaction1));

        connection.asyncSend(
                request,
                response -> {
                    assertEquals(response.getErrorCode(), BCOSStatusCode.Success);
                    TransactionReceipt transactionReceipt = null;
                    try {
                        transactionReceipt =
                                ObjectMapperFactory.getObjectMapper()
                                        .readValue(response.getData(), TransactionReceipt.class);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    String blockNumber = transactionReceipt.getBlockNumber();
                    assertEquals(Numeric.decodeQuantity(blockNumber).longValue(), 9);
                    assertEquals(
                            transactionReceipt.getTransactionHash(),
                            "0x8b3946912d1133f9fb0722a7b607db2456d468386c2e86b035e81ef91d94eb90");
                    assertFalse(transactionReceipt.getTxProof().isEmpty());
                    assertFalse(transactionReceipt.getReceiptProof().isEmpty());
                });
    }

    @Test
    public void handleFailedSendTransactionTest() throws IOException, InterruptedException {

        AbstractClientWrapper clientWrapper = new ClientWrapperWithExceptionMock();
        CryptoSuite cryptoSuite = clientWrapper.getCryptoSuite();
        BCOSConnection connection =
                new BCOSConnection(
                        clientWrapper,
                        new ScheduledThreadPoolExecutor(
                                1, new CustomizableThreadFactory(this.getClass().getName())));
        Request request = new Request();
        request.setType(BCOSRequestType.SEND_TRANSACTION);

        String address = "0x6db416c8ac6b1fe7ed08771de419b71c084ee5969029346806324601f2e3f0d0";
        String funName = "funcName";
        String[] params = new String[] {"abc", "def", "hig"};

        Function function = FunctionUtility.newDefaultFunction(funName, params);

        FunctionEncoder functionEncoder = new FunctionEncoder(cryptoSuite);
        String abi = functionEncoder.encode(function);

        RawTransaction rawTransaction =
                SignTransaction.buildTransaction(
                        address,
                        BigInteger.valueOf(1),
                        BigInteger.valueOf(1),
                        BigInteger.valueOf(1),
                        abi);
        TransactionEncoderService transactionEncoderService =
                new TransactionEncoderService(cryptoSuite);
        CryptoKeyPair credentials = cryptoSuite.getCryptoKeyPair();
        String sign = transactionEncoderService.encodeAndSign(rawTransaction, credentials);

        TransactionRequest transactionRequest = new TransactionRequest();
        transactionRequest.setMethod(funName);
        transactionRequest.setArgs(params);
        TransactionParams transaction1 =
                new TransactionParams(transactionRequest, sign, TransactionParams.SUB_TYPE.SEND_TX);

        request.setData(ObjectMapperFactory.getObjectMapper().writeValueAsBytes(transaction1));

        AsyncToSync asyncToSync = new AsyncToSync();
        connection.asyncSend(
                request,
                response -> {
                    assertEquals(
                            response.getErrorCode(), BCOSStatusCode.HandleSendTransactionFailed);
                    asyncToSync.getSemaphore().release();
                });
        asyncToSync.getSemaphore().acquire();
    }

    @Test
    public void handleFailedSendTransactionTest0() throws IOException, InterruptedException {

        AbstractClientWrapper clientWrapper = new ClientWrapperWithNullMock();
        CryptoSuite cryptoSuite = clientWrapper.getCryptoSuite();
        BCOSConnection connection =
                new BCOSConnection(
                        clientWrapper,
                        new ScheduledThreadPoolExecutor(
                                1, new CustomizableThreadFactory(this.getClass().getName())));
        Request request = new Request();
        request.setType(BCOSRequestType.SEND_TRANSACTION);

        String address = "0x6db416c8ac6b1fe7ed08771de419b71c084ee5969029346806324601f2e3f0d0";
        String funName = "funcName";
        String[] params = new String[] {"abc", "def", "hig"};

        Function function = FunctionUtility.newDefaultFunction(funName, params);

        FunctionEncoder functionEncoder = new FunctionEncoder(cryptoSuite);
        String abi = functionEncoder.encode(function);

        RawTransaction rawTransaction =
                SignTransaction.buildTransaction(
                        address,
                        BigInteger.valueOf(1),
                        BigInteger.valueOf(1),
                        BigInteger.valueOf(1),
                        abi);
        TransactionEncoderService transactionEncoderService =
                new TransactionEncoderService(cryptoSuite);
        CryptoKeyPair credentials = cryptoSuite.getCryptoKeyPair();
        String sign = transactionEncoderService.encodeAndSign(rawTransaction, credentials);

        TransactionRequest transactionRequest = new TransactionRequest();
        transactionRequest.setMethod(funName);
        transactionRequest.setArgs(params);
        TransactionParams transaction1 =
                new TransactionParams(transactionRequest, sign, TransactionParams.SUB_TYPE.SEND_TX);

        request.setData(ObjectMapperFactory.getObjectMapper().writeValueAsBytes(transaction1));

        AsyncToSync asyncToSync = new AsyncToSync();
        connection.asyncSend(
                request,
                response -> {
                    assertEquals(
                            response.getErrorCode(), BCOSStatusCode.TransactionReceiptNotExist);
                    asyncToSync.getSemaphore().release();
                });
        asyncToSync.getSemaphore().acquire();
    }

    @Test
    public void handleFailedGetTransactionProofTest() throws IOException, InterruptedException {
        String hash = "0x633a3386a189455354c058af6606d705697f3b216ad555958dc680f68cc4e99d";
        Request request = new Request();
        request.setType(BCOSRequestType.GET_TRANSACTION_PROOF);
        request.setData(hash.getBytes(StandardCharsets.UTF_8));

        AbstractClientWrapper clientWrapper = new ClientWrapperWithExceptionMock();
        BCOSConnection connection =
                new BCOSConnection(
                        clientWrapper,
                        new ScheduledThreadPoolExecutor(
                                1, new CustomizableThreadFactory(this.getClass().getName())));

        connection.asyncSend(
                request,
                response ->
                        assertEquals(response.getErrorCode(), BCOSStatusCode.UnclassifiedError));
    }
}
