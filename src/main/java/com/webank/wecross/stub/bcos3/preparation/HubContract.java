package com.webank.wecross.stub.bcos3.preparation;

import com.webank.wecross.stub.bcos3.BCOSBaseStubFactory;
import com.webank.wecross.stub.bcos3.BCOSConnection;
import com.webank.wecross.stub.bcos3.BCOSConnectionFactory;
import com.webank.wecross.stub.bcos3.account.BCOSAccount;
import com.webank.wecross.stub.bcos3.client.AbstractClientWrapper;
import com.webank.wecross.stub.bcos3.client.ClientWrapperFactory;
import com.webank.wecross.stub.bcos3.common.BCOSConstant;
import com.webank.wecross.stub.bcos3.config.BCOSStubConfig;
import com.webank.wecross.stub.bcos3.config.BCOSStubConfigParser;
import com.webank.wecross.stub.bcos3.contract.SignTransaction;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.model.PrecompiledRetCode;
import org.fisco.bcos.sdk.v3.model.RetCode;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.model.callback.TransactionCallback;
import org.fisco.bcos.sdk.v3.transaction.codec.encode.TransactionEncoderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.io.File;
import java.math.BigInteger;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HubContract {

    private static final Logger logger = LoggerFactory.getLogger(HubContract.class);

    private String hubContractFile;
    private String chainPath;

    private BCOSAccount account;
    private BCOSConnection connection;

    public HubContract(String hubContractFile, String chainPath, String accountName)
            throws Exception {
        this.hubContractFile = hubContractFile;
        this.chainPath = chainPath;

        BCOSStubConfigParser bcosStubConfigParser =
                new BCOSStubConfigParser(chainPath, "stub.toml");
        BCOSStubConfig bcosStubConfig = bcosStubConfigParser.loadConfig();

        boolean isGMStub = bcosStubConfig.isGMStub();
        int cryptoType = isGMStub ? CryptoType.SM_TYPE : CryptoType.ECDSA_TYPE;
        String alg = isGMStub ? BCOSConstant.SM2P256V1 : BCOSConstant.SECP256K1;
        String stubType = bcosStubConfig.getType();
        BCOSBaseStubFactory bcosBaseStubFactory =
                new BCOSBaseStubFactory(cryptoType, alg, stubType);

        AbstractClientWrapper clientWrapper =
                ClientWrapperFactory.createClientWrapperInstance(bcosStubConfig);

        account =
                (BCOSAccount)
                        bcosBaseStubFactory.newAccount(
                                accountName,
                                "classpath:" + chainPath + File.separator + accountName);
        if (account == null) {
            account =
                    (BCOSAccount)
                            bcosBaseStubFactory.newAccount(
                                    accountName,
                                    "classpath:accounts" + File.separator + accountName);
        }

        ScheduledExecutorService scheduledExecutorService =
                new ScheduledThreadPoolExecutor(4, new CustomizableThreadFactory("tmpBCOSConn-"));
        connection =
                BCOSConnectionFactory.build(
                        bcosStubConfig, clientWrapper, scheduledExecutorService);

        if (account == null) {
            throw new Exception("Account " + accountName + " not found");
        }

        if (connection == null) {
            throw new Exception("Init connection exception, please check log");
        }
    }

    public HubContract() {
    }

    public BCOSAccount getAccount() {
        return account;
    }

    public void setAccount(BCOSAccount account) {
        this.account = account;
    }

    public BCOSConnection getConnection() {
        return connection;
    }

    public void setConnection(BCOSConnection connection) {
        this.connection = connection;
    }

    /**
     * @param solFile, String contractName
     * @return
     */
    public CnsInfo deployContractAndRegisterCNS(
            File solFile, String contractName, String cnsName, String cnsVersion) throws Exception {

        logger.info("cnsName: {}, cnsVersion: {}", cnsName, cnsVersion);

        AbstractClientWrapper clientWrapper = connection.getClientWrapper();
        Client client = clientWrapper.getClient();

        /** First compile the contract source code */
        SolidityCompiler.Result res =
                SolidityCompiler.compile(
                        solFile,
                        client.getCryptoSuite().getCryptoTypeConfig() == CryptoType.SM_TYPE,
                        true,
                        SolidityCompiler.Options.ABI,
                        SolidityCompiler.Options.BIN,
                        SolidityCompiler.Options.INTERFACE,
                        SolidityCompiler.Options.METADATA);

        if (res.isFailed()) {
            throw new RuntimeException(" compiling contract failed, " + res.getErrors());
        }

        CompilationResult.ContractMetadata metadata =
                CompilationResult.parse(res.getOutput()).getContract(contractName);

        /** deploy the contract by sendTransaction */
        // groupId
        BigInteger groupID =
                new BigInteger(connection.getProperties().get(BCOSConstant.BCOS_GROUP_ID));
        // chainId
        BigInteger chainID =
                new BigInteger(connection.getProperties().get(BCOSConstant.BCOS_CHAIN_ID));

        BigInteger blockNumber = clientWrapper.getBlockNumber();

        logger.info(
                " groupID: {}, chainID: {}, blockNumber: {}, accountAddress: {}, bin: {}, abi: {}",
                chainID,
                groupID,
                blockNumber,
                account.getCredentials().getAddress(),
                metadata.bin,
                metadata.abi);

        RawTransaction rawTransaction =
                SignTransaction.buildTransaction(null, groupID, chainID, blockNumber, metadata.bin);
        CryptoKeyPair credentials = account.getCredentials();

        TransactionEncoderService transactionEncoderService =
                new TransactionEncoderService(client.getCryptoSuite());
        String signTx = transactionEncoderService.encodeAndSign(rawTransaction, credentials);

        CompletableFuture<String> completableFuture = new CompletableFuture<>();
        clientWrapper.sendTransaction(
                signTx,
                new TransactionCallback() {
                    @Override
                    public void onResponse(TransactionReceipt receipt) {
                        if (!receipt.isStatusOK()) {
                            logger.error(
                                    " deploy contract failed, error status: {}, error message: {} ",
                                    receipt.getStatus(),
                                    receipt.getMessage());
                            completableFuture.complete(null);
                        } else {
                            logger.info(
                                    " deploy contract success, contractAddress: {}",
                                    receipt.getContractAddress());
                            completableFuture.complete(receipt.getContractAddress());
                        }
                    }
                });

        String contractAddress = completableFuture.get(10, TimeUnit.SECONDS);
        if (Objects.isNull(contractAddress)) {
            throw new Exception("Failed to deploy hub contract.");
        }
        CnsService cnsService = new CnsService(client, account.getCredentials());
        RetCode retCode =
                cnsService.registerCNS(cnsName, cnsVersion, contractAddress, metadata.abi);

        if (retCode.getCode() < PrecompiledRetCode.CODE_SUCCESS.getCode()) {
            throw new RuntimeException(" registerCns failed, error message: " + retCode);
        }

        CnsInfo cnsInfo = new CnsInfo();
        cnsInfo.setName(cnsName);
        cnsInfo.setVersion(cnsVersion);
        cnsInfo.setAddress(contractAddress);
        cnsInfo.setAbi(metadata.abi);
        return cnsInfo;
    }

    public void deploy() throws Exception {
        if (!connection.hasHubDeployed()) {
            System.out.println("Deploy WeCrossHub to chain " + chainPath + " ...");

            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver();
            File file = resolver.getResource("classpath:" + hubContractFile).getFile();
            String version = String.valueOf(System.currentTimeMillis() / 1000);

            deployContractAndRegisterCNS(
                    file, BCOSConstant.BCOS_HUB_NAME, BCOSConstant.BCOS_HUB_NAME, version);
            System.out.println(
                    "SUCCESS: WeCrossHub:" + version + " has been deployed! chain: " + chainPath);
        } else {
            System.out.println(
                    "SUCCESS: WeCrossHub has already been deployed! chain: " + chainPath);
        }
    }

    public void upgrade() throws Exception {

        System.out.println("Upgrade WeCrossHub to chain " + chainPath + " ...");

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        File file = resolver.getResource("classpath:" + hubContractFile).getFile();
        String version = String.valueOf(System.currentTimeMillis() / 1000);

        deployContractAndRegisterCNS(
                file, BCOSConstant.BCOS_HUB_NAME, BCOSConstant.BCOS_HUB_NAME, version);

        System.out.println(
                "SUCCESS: WeCrossHub:" + version + " has been upgraded! chain: " + chainPath);
    }

    public void getHubAddress() {
        try {
            if (!connection.hasHubDeployed()) {
                System.out.println("WeCrossHub has not been deployed");
            } else {
                System.out.println("WeCrossHub address: " + connection.getHubAddress());
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}
