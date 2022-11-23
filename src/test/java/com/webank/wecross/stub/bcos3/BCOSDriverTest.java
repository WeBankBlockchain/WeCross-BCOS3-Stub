package com.webank.wecross.stub.bcos3;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.BlockHeader;
import com.webank.wecross.stub.BlockManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.Path;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionException;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.TransactionResponse;
import com.webank.wecross.stub.bcos3.account.BCOSAccount;
import com.webank.wecross.stub.bcos3.account.BCOSAccountFactory;
import com.webank.wecross.stub.bcos3.client.ClientDefaultConfig;
import com.webank.wecross.stub.bcos3.client.ClientWrapperCallNotSucStatus;
import com.webank.wecross.stub.bcos3.client.ClientWrapperImplMock;
import com.webank.wecross.stub.bcos3.client.ClientWrapperTxVerifyMock;
import com.webank.wecross.stub.bcos3.client.ClientWrapperWithExceptionMock;
import com.webank.wecross.stub.bcos3.client.ClientWrapperWithNullMock;
import com.webank.wecross.stub.bcos3.common.BCOSConstant;
import com.webank.wecross.stub.bcos3.common.BCOSRequestType;
import com.webank.wecross.stub.bcos3.common.BCOSStatusCode;
import com.webank.wecross.stub.bcos3.common.BCOSStubException;
import com.webank.wecross.stub.bcos3.common.ObjectMapperFactory;
import com.webank.wecross.stub.bcos3.config.BCOSStubConfig;
import com.webank.wecross.stub.bcos3.config.BCOSStubConfigParser;
import com.webank.wecross.stub.bcos3.contract.FunctionUtility;
import com.webank.wecross.stub.bcos3.contract.SignTransaction;
import com.webank.wecross.stub.bcos3.protocol.request.TransactionParams;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.apache.commons.lang3.tuple.Pair;
import org.fisco.bcos.sdk.v3.codec.abi.FunctionEncoder;
import org.fisco.bcos.sdk.v3.codec.datatypes.Function;
import org.fisco.bcos.sdk.v3.codec.wrapper.ABIDefinition;
import org.fisco.bcos.sdk.v3.codec.wrapper.ABIDefinitionFactory;
import org.fisco.bcos.sdk.v3.codec.wrapper.ABIObject;
import org.fisco.bcos.sdk.v3.codec.wrapper.ABIObjectFactory;
import org.fisco.bcos.sdk.v3.codec.wrapper.ContractABIDefinition;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.transaction.codec.encode.TransactionEncoderService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

public class BCOSDriverTest {

    private Driver driver = null;
    private BCOSAccount account = null;
    private Connection connection = null;
    private Connection exceptionConnection = null;
    private Connection callNotOkStatusConnection = null;
    private Connection nonExistConnection = null;
    private Connection txVerifyConnection = null;
    private ResourceInfo resourceInfo = null;
    private BlockManager blockManager = null;
    private BlockManager txVerifyBlockManager = null;
    private TransactionContext transactionContext = null;
    private CryptoSuite cryptoSuite = null;
    private ABIDefinitionFactory abiDefinitionFactory = null;
    private FunctionEncoder functionEncoder = null;
    private TransactionEncoderService transactionEncoderService = null;

    public TransactionRequest createTransactionRequest(String method, String[] args) {
        TransactionRequest transactionRequest = new TransactionRequest(method, args);
        return transactionRequest;
    }

    @Before
    public void initializer() throws Exception {
        BCOS3EcdsaEvmStubFactory bcosSubFactory = new BCOS3EcdsaEvmStubFactory();
        Path path = Path.decode("a.b.c");
        driver = bcosSubFactory.newDriver();

        BCOSStubConfigParser bcosStubConfigParser =
                new BCOSStubConfigParser("./", "stub-sample-ut.toml");
        BCOSStubConfig bcosStubConfig = bcosStubConfigParser.loadConfig();
        cryptoSuite =
                bcosStubConfig.isGMStub()
                        ? new CryptoSuite(CryptoType.SM_TYPE)
                        : new CryptoSuite(CryptoType.ECDSA_TYPE);
        abiDefinitionFactory = new ABIDefinitionFactory(cryptoSuite);
        functionEncoder = new FunctionEncoder(cryptoSuite);
        transactionEncoderService = new TransactionEncoderService(cryptoSuite);
        account =
                BCOSAccountFactory.getInstance(cryptoSuite)
                        .build("bcos", "classpath:/accounts/bcos");

        ScheduledExecutorService scheduledExecutorService =
                new ScheduledThreadPoolExecutor(4, new CustomizableThreadFactory("tmpBCOSConn-"));
        connection = BCOSConnectionFactory.build(bcosStubConfig, new ClientWrapperImplMock());
        ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
        connection
                .getProperties()
                .put(
                        "VERIFIER",
                        "{\"chainType\":\"BCOS2.0\",\"pubKey\":["
                                + "\"11e1be251ca08bb44f36fdeedfaeca40894ff80dfd80084607a75509edeaf2a9c6fee914f1e9efda571611cf4575a1577957edfd2baa9386bd63eb034868625f\","
                                + "\"78a313b426c3de3267d72b53c044fa9fe70c2a27a00af7fea4a549a7d65210ed90512fc92b6194c14766366d434235c794289d66deff0796f15228e0e14a9191\","
                                + "\"95b7ff064f91de76598f90bc059bec1834f0d9eeb0d05e1086d49af1f9c2f321062d011ee8b0df7644bd54c4f9ca3d8515a3129bbb9d0df8287c9fa69552887e\","
                                + "\"b8acb51b9fe84f88d670646be36f31c52e67544ce56faf3dc8ea4cf1b0ebff0864c6b218fdcd9cf9891ebd414a995847911bd26a770f429300085f37e1131f36\"]}");

        connection.getProperties().put(BCOSConstant.BCOS_STUB_TYPE, "BCOS2.0");
        exceptionConnection =
                BCOSConnectionFactory.build(bcosStubConfig, new ClientWrapperWithExceptionMock());
        nonExistConnection =
                BCOSConnectionFactory.build(bcosStubConfig, new ClientWrapperWithNullMock());
        callNotOkStatusConnection =
                BCOSConnectionFactory.build(bcosStubConfig, new ClientWrapperCallNotSucStatus());
        txVerifyConnection =
                BCOSConnectionFactory.build(bcosStubConfig, new ClientWrapperTxVerifyMock());

        blockManager = new BlockManagerImplMock(new ClientWrapperImplMock());
        txVerifyBlockManager = new BlockManagerImplMock(new ClientWrapperTxVerifyMock());
        resourceInfo = ((BCOSConnection) connection).getResourceInfoList().get(0);

        transactionContext = new TransactionContext(account, path, resourceInfo, blockManager);
    }

    @Test
    public void decodeCallTransactionRequestTest() throws Exception {
        String func = "func";
        String[] params = new String[] {"a", "b", "c"};

        TransactionRequest request = new TransactionRequest(func, params);
        Function function = FunctionUtility.newDefaultFunction(func, params);

        TransactionParams transaction =
                new TransactionParams(
                        request, functionEncoder.encode(function), TransactionParams.SUB_TYPE.CALL);

        byte[] data = ObjectMapperFactory.getObjectMapper().writeValueAsBytes(transaction);

        Pair<Boolean, TransactionRequest> booleanTransactionRequestPair =
                driver.decodeTransactionRequest(Request.newRequest(BCOSRequestType.CALL, data));
        assertTrue(booleanTransactionRequestPair.getKey() == true);
        assertEquals(booleanTransactionRequestPair.getValue().getMethod(), func);
        assertEquals(booleanTransactionRequestPair.getValue().getArgs().length, params.length);
    }

    @Test
    public void decodeSendTransactionTransactionRequestTest() throws Exception {
        String func = "func";
        String[] params = new String[] {"a", "b", "c"};

        TransactionRequest request = new TransactionRequest(func, params);
        Function function = FunctionUtility.newDefaultFunction(func, params);

        RawTransaction rawTransaction =
                SignTransaction.buildTransaction(
                        "0x0",
                        BigInteger.valueOf(ClientDefaultConfig.DEFAULT_GROUP_ID),
                        BigInteger.valueOf(ClientDefaultConfig.DEFAULT_CHAIN_ID),
                        BigInteger.valueOf(1111),
                        functionEncoder.encode(function));
        String signTx =
                transactionEncoderService.encodeAndSign(rawTransaction, account.getCredentials());

        TransactionParams transaction =
                new TransactionParams(request, signTx, TransactionParams.SUB_TYPE.SEND_TX);

        byte[] data = ObjectMapperFactory.getObjectMapper().writeValueAsBytes(transaction);

        Pair<Boolean, TransactionRequest> booleanTransactionRequestPair =
                driver.decodeTransactionRequest(
                        Request.newRequest(BCOSRequestType.SEND_TRANSACTION, data));
        assertTrue(booleanTransactionRequestPair.getKey() == true);
        assertEquals(booleanTransactionRequestPair.getValue().getMethod(), func);
        assertEquals(booleanTransactionRequestPair.getValue().getArgs().length, params.length);
    }

    @Test
    public void decodeProxySendTransactionTransactionRequestTest() throws Exception {
        String func = "set";
        String[] params = new String[] {"a"};

        String abi =
                "[{\"constant\":false,\"inputs\":[{\"name\":\"n\",\"type\":\"string\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}]";

        ContractABIDefinition contractABIDefinition = abiDefinitionFactory.loadABI(abi);
        ABIDefinition abiDefinition = contractABIDefinition.getFunctions().get("set").get(0);
        ABIObject inputObject = ABIObjectFactory.createInputObject(abiDefinition);
        ABICodecJsonWrapper abiCodecJsonWrapper = new ABICodecJsonWrapper();
        ABIObject encoded = abiCodecJsonWrapper.encode(inputObject, Arrays.asList(params));

        TransactionRequest request = new TransactionRequest(func, params);
        Function function =
                FunctionUtility.newSendTransactionProxyFunction(
                        "1", "1", 1, "a.b.Hello", "set(string)", encoded.encode());

        RawTransaction rawTransaction =
                SignTransaction.buildTransaction(
                        "0x0",
                        BigInteger.valueOf(ClientDefaultConfig.DEFAULT_GROUP_ID),
                        BigInteger.valueOf(ClientDefaultConfig.DEFAULT_CHAIN_ID),
                        BigInteger.valueOf(1111),
                        functionEncoder.encode(function));
        String signTx =
                transactionEncoderService.encodeAndSign(rawTransaction, account.getCredentials());

        TransactionParams transaction =
                new TransactionParams(request, signTx, TransactionParams.SUB_TYPE.SEND_TX_BY_PROXY);
        transaction.setAbi(abi);

        byte[] data = ObjectMapperFactory.getObjectMapper().writeValueAsBytes(transaction);
        Pair<Boolean, TransactionRequest> booleanTransactionRequestPair =
                driver.decodeTransactionRequest(
                        Request.newRequest(BCOSRequestType.SEND_TRANSACTION, data));
        assertTrue(booleanTransactionRequestPair.getKey());
        assertEquals(booleanTransactionRequestPair.getValue().getMethod(), func);
        assertEquals(booleanTransactionRequestPair.getValue().getArgs().length, params.length);
        assertEquals(booleanTransactionRequestPair.getValue().getArgs()[0], params[0]);
    }

    @Test
    public void decodeProxyCallTransactionRequestTest() throws Exception {
        String func = "set";
        String[] params = new String[] {"a"};

        String abi =
                "[{\"constant\":false,\"inputs\":[{\"name\":\"n\",\"type\":\"string\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}]";

        ContractABIDefinition contractABIDefinition = abiDefinitionFactory.loadABI(abi);
        ABIDefinition abiDefinition = contractABIDefinition.getFunctions().get("set").get(0);
        ABIObject inputObject = ABIObjectFactory.createInputObject(abiDefinition);
        ABICodecJsonWrapper abiCodecJsonWrapper = new ABICodecJsonWrapper();
        ABIObject encoded = abiCodecJsonWrapper.encode(inputObject, Arrays.asList(params));

        Function function =
                FunctionUtility.newConstantCallProxyFunction(
                        "1", "a.b.Hello", "set(string)", encoded.encode());

        TransactionRequest request = new TransactionRequest(func, params);

        TransactionParams transaction =
                new TransactionParams(
                        request,
                        functionEncoder.encode(function),
                        TransactionParams.SUB_TYPE.CALL_BY_PROXY);
        transaction.setAbi(abi);

        byte[] data = ObjectMapperFactory.getObjectMapper().writeValueAsBytes(transaction);
        Pair<Boolean, TransactionRequest> booleanTransactionRequestPair =
                driver.decodeTransactionRequest(Request.newRequest(BCOSRequestType.CALL, data));
        assertTrue(booleanTransactionRequestPair.getKey() == true);
        assertEquals(booleanTransactionRequestPair.getValue().getMethod(), func);
        assertEquals(booleanTransactionRequestPair.getValue().getArgs().length, params.length);
        assertEquals(booleanTransactionRequestPair.getValue().getArgs()[0], params[0]);
    }

    @Test
    public void getBlockNumberTest() {
        Request request = new Request();
        request.setType(BCOSRequestType.GET_BLOCK_NUMBER);

        driver.asyncGetBlockNumber(
                connection, (e, blockNumber) -> assertEquals(blockNumber, 11111));
    }

    @Test
    public void getBlockNumberFailedTest() {
        Request request = new Request();
        request.setType(BCOSRequestType.GET_BLOCK_NUMBER);

        driver.asyncGetBlockNumber(exceptionConnection, (e, blockNumber) -> assertNotNull(e));
    }

    @Test
    public void getBlockHeaderTest() throws IOException {

        Request request = new Request();
        request.setType(BCOSRequestType.GET_BLOCK_BY_NUMBER);
        request.setData(BigInteger.valueOf(11111).toByteArray());

        driver.asyncGetBlock(
                1111,
                false,
                connection,
                (e, block) -> {
                    assertTrue(Objects.isNull(e));
                    assertTrue(Objects.nonNull(block));
                    assertTrue(block.getRawBytes().length > 1);
                    assertTrue(!block.getTransactionsHashes().isEmpty());

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
    public void getBlockTest() throws IOException {

        Request request = new Request();
        request.setType(BCOSRequestType.GET_BLOCK_BY_NUMBER);
        request.setData(BigInteger.valueOf(11111).toByteArray());

        driver.asyncGetBlock(
                1111,
                false,
                connection,
                (e, block) -> {
                    assertTrue(Objects.isNull(e));
                    assertTrue(Objects.nonNull(block));
                    assertTrue(block.getRawBytes().length > 1);
                    assertFalse(block.getTransactionsHashes().isEmpty());

                    BlockHeader blockHeader = block.getBlockHeader();
                    assertTrue(!block.transactionsHashes.isEmpty());
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
    public void getBlockHeaderFailedTest() throws IOException {

        Request request = new Request();
        request.setType(BCOSRequestType.GET_BLOCK_BY_NUMBER);
        request.setData(BigInteger.valueOf(11111).toByteArray());

        driver.asyncGetBlock(
                1111, false, exceptionConnection, (e, block) -> assertTrue(Objects.isNull(block)));
    }

    @Test
    public void callFailedTest() throws InterruptedException {

        Request request = new Request();
        request.setType(BCOSRequestType.CALL);

        String address = "0x6db416c8ac6b1fe7ed08771de419b71c084ee5969029346806324601f2e3f0d0";
        String funName = "funcName";
        String[] params = new String[] {"abc", "def", "hig", "xxxxx"};

        TransactionRequest transactionRequest = createTransactionRequest(funName, params);

        AsyncToSync asyncToSync = new AsyncToSync();

        driver.asyncCall(
                transactionContext,
                transactionRequest,
                false,
                exceptionConnection,
                new Driver.Callback() {
                    @Override
                    public void onTransactionResponse(
                            TransactionException transactionException,
                            TransactionResponse transactionResponse) {
                        assertTrue(Objects.isNull(transactionResponse));
                        asyncToSync.getSemaphore().release();
                    }
                });

        asyncToSync.getSemaphore().acquire();
    }

    @Test
    public void getVerifyTransactionTest() throws Exception {
        String transactionHash =
                "0x8b3946912d1133f9fb0722a7b607db2456d468386c2e86b035e81ef91d94eb90";
        long blockNumber = 9;
        driver.asyncGetTransaction(
                transactionHash,
                blockNumber,
                txVerifyBlockManager,
                true,
                txVerifyConnection,
                (e, verifiedTransaction) -> {
                    assertEquals(
                            verifiedTransaction.getTransactionResponse().getBlockNumber(),
                            blockNumber);
                    assertEquals(
                            verifiedTransaction.getTransactionResponse().getHash(),
                            transactionHash);
                });
    }

    @Test
    public void getVerifyTransactionExceptionTest() throws Exception {
        String transactionHash =
                "0x6db416c8ac6b1fe7ed08771de419b71c084ee5969029346806324601f2e3f0d0";
        long blockNumber = 11111;
        driver.asyncGetTransaction(
                transactionHash,
                blockNumber,
                blockManager,
                true,
                exceptionConnection,
                (e, verifiedTransaction) -> assertTrue(Objects.isNull(verifiedTransaction)));
    }

    @Test
    public void getVerifyTransactionNotExistTest() throws Exception {
        String transactionHash =
                "0x6db416c8ac6b1fe7ed08771de419b71c084ee5969029346806324601f2e3f0d0";
        long blockNumber = 11111;
        driver.asyncGetTransaction(
                transactionHash,
                blockNumber,
                blockManager,
                true,
                nonExistConnection,
                (e, verifiedTransaction) -> assertTrue(Objects.isNull(verifiedTransaction)));
    }

    public TransactionContext createTransactionContext(
            Account account, BlockManager blockManager, ResourceInfo resourceInfo) {
        TransactionContext transactionContext =
                new TransactionContext(account, null, resourceInfo, blockManager);
        return transactionContext;
    }

    @Test
    public void checkRequestTest() {

        BCOSDriver bcosDriver = (BCOSDriver) driver;
        try {
            bcosDriver.checkTransactionRequest(transactionContext, null);
        } catch (BCOSStubException e) {
            assertTrue(e.getErrorCode().intValue() == BCOSStatusCode.InvalidParameter);
            assertTrue(e.getMessage().equals("TransactionRequest is null"));
        }

        try {
            bcosDriver.checkTransactionRequest(null, null);
        } catch (BCOSStubException e) {
            assertTrue(e.getErrorCode().intValue() == BCOSStatusCode.InvalidParameter);
            assertTrue(e.getMessage().equals("TransactionContext is null"));
        }

        try {
            TransactionContext transactionContext =
                    createTransactionContext(null, blockManager, resourceInfo);
            bcosDriver.checkTransactionRequest(transactionContext, new TransactionRequest());
        } catch (BCOSStubException e) {
            assertTrue(e.getErrorCode().intValue() == BCOSStatusCode.InvalidParameter);
            assertTrue(e.getMessage().equals("Account is null"));
        }

        try {
            TransactionContext transactionContext =
                    createTransactionContext(account, null, resourceInfo);
            bcosDriver.checkTransactionRequest(transactionContext, new TransactionRequest());
        } catch (BCOSStubException e) {
            assertTrue(e.getErrorCode().intValue() == BCOSStatusCode.InvalidParameter);
            assertTrue(e.getMessage().equals("BlockHeaderManager is null"));
        }

        try {
            TransactionRequest transactionRequest = new TransactionRequest(null, null);
            bcosDriver.checkTransactionRequest(transactionContext, transactionRequest);
        } catch (BCOSStubException e) {
            assertTrue(e.getErrorCode().intValue() == BCOSStatusCode.InvalidParameter);
            assertTrue(e.getMessage().equals("Method is null"));
        }
    }
}
