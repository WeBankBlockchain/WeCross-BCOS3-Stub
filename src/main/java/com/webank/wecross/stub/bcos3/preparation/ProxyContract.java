package com.webank.wecross.stub.bcos3.preparation;

import com.webank.wecross.stub.bcos3.BCOSBaseStubFactory;
import com.webank.wecross.stub.bcos3.BCOSConnection;
import com.webank.wecross.stub.bcos3.BCOSConnectionFactory;
import com.webank.wecross.stub.bcos3.account.BCOSAccount;
import com.webank.wecross.stub.bcos3.client.AbstractClientWrapper;
import com.webank.wecross.stub.bcos3.client.ClientWrapperFactory;
import com.webank.wecross.stub.bcos3.common.BCOSConstant;
import com.webank.wecross.stub.bcos3.common.StatusCode;
import com.webank.wecross.stub.bcos3.config.BCOSStubConfig;
import com.webank.wecross.stub.bcos3.config.BCOSStubConfigParser;
import com.webank.wecross.stub.bcos3.contract.SignTransaction;
import java.io.File;
import java.math.BigInteger;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.contract.precompiled.cns.CnsInfo;
import org.fisco.bcos.sdk.contract.precompiled.cns.CnsService;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.model.CryptoType;
import org.fisco.bcos.sdk.model.PrecompiledRetCode;
import org.fisco.bcos.sdk.model.RetCode;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.fisco.bcos.sdk.model.callback.TransactionCallback;
import org.fisco.bcos.sdk.transaction.codec.encode.TransactionEncoderService;
import org.fisco.bcos.sdk.transaction.model.po.RawTransaction;
import org.fisco.solc.compiler.CompilationResult;
import org.fisco.solc.compiler.SolidityCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

public class ProxyContract {

    private static final Logger logger = LoggerFactory.getLogger(ProxyContract.class);

    private String proxyContractFile;
    private String chainPath;

    private BCOSAccount account;
    private BCOSConnection connection;

    public ProxyContract(String proxyContractFile, String chainPath, String accountName)
            throws Exception {
        this.proxyContractFile = proxyContractFile;
        this.chainPath = chainPath;
        BCOSStubConfigParser bcosStubConfigParser =
                new BCOSStubConfigParser(chainPath, "stub.toml");
        BCOSStubConfig bcosStubConfig = bcosStubConfigParser.loadConfig();

        boolean isGMStub = bcosStubConfig.isGMStub();
        AbstractClientWrapper clientWrapper =
                ClientWrapperFactory.createClientWrapperInstance(bcosStubConfig);

        BCOSBaseStubFactory bcosBaseStubFactory =
                isGMStub
                        ? new BCOSBaseStubFactory(CryptoType.SM_TYPE, "sm2p256v1", "GM_BCOS2.0")
                        : new BCOSBaseStubFactory(CryptoType.ECDSA_TYPE, "secp256k1", "BCOS2.0");

        account =
                (BCOSAccount)
                        bcosBaseStubFactory.newAccount(
                                accountName,
                                "classpath:" + chainPath + File.separator + accountName);
        if (account == null) {
            System.out.println("Not f");
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

        if (!bcosStubConfig.getType().equals(account.getType())) {
            throw new Exception(
                    "Account type "
                            + account.getType()
                            + " and chain type "
                            + bcosStubConfig.getType()
                            + " are not the same.");
        }
    }

    public ProxyContract() {}

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
                                    StatusCode.getStatusMessage(receipt.getStatus()));
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
            throw new Exception("Failed to deploy proxy contract.");
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
        if (!connection.hasProxyDeployed()) {
            System.out.println("Deploy WeCrossProxy to chain " + chainPath + " ...");

            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver();
            File file = resolver.getResource("classpath:" + proxyContractFile).getFile();
            String version = String.valueOf(System.currentTimeMillis() / 1000);

            deployContractAndRegisterCNS(file, "WeCrossProxy", "WeCrossProxy", version);
            System.out.println(
                    "SUCCESS: WeCrossProxy:" + version + " has been deployed! chain: " + chainPath);
        } else {
            System.out.println(
                    "SUCCESS: WeCrossProxy has already been deployed! chain: " + chainPath);
        }
    }

    public void upgrade() throws Exception {

        System.out.println("Upgrade WeCrossProxy to chain " + chainPath + " ...");

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        File file = resolver.getResource("classpath:" + proxyContractFile).getFile();
        String version = String.valueOf(System.currentTimeMillis() / 1000);

        deployContractAndRegisterCNS(file, "WeCrossProxy", "WeCrossProxy", version);

        System.out.println(
                "SUCCESS: WeCrossProxy:" + version + " has been upgraded! chain: " + chainPath);
    }
}
