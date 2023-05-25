package com.webank.wecross.stub.bcos3.preparation;

import static org.fisco.bcos.sdk.v3.client.protocol.model.TransactionAttribute.LIQUID_CREATE;
import static org.fisco.bcos.sdk.v3.client.protocol.model.TransactionAttribute.LIQUID_SCALE_CODEC;

import com.webank.wecross.stub.bcos3.BCOSBaseStubFactory;
import com.webank.wecross.stub.bcos3.BCOSConnection;
import com.webank.wecross.stub.bcos3.BCOSConnectionFactory;
import com.webank.wecross.stub.bcos3.account.BCOSAccount;
import com.webank.wecross.stub.bcos3.client.AbstractClientWrapper;
import com.webank.wecross.stub.bcos3.client.ClientWrapperFactory;
import com.webank.wecross.stub.bcos3.common.BCOSConstant;
import com.webank.wecross.stub.bcos3.config.BCOSStubConfig;
import com.webank.wecross.stub.bcos3.config.BCOSStubConfigParser;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.fisco.bcos.sdk.jni.utilities.tx.TransactionBuilderJniObj;
import org.fisco.bcos.sdk.jni.utilities.tx.TxPair;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.contract.precompiled.bfs.BFSInfo;
import org.fisco.bcos.sdk.v3.contract.precompiled.bfs.BFSService;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.model.PrecompiledRetCode;
import org.fisco.bcos.sdk.v3.model.RetCode;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.model.TransactionReceiptStatus;
import org.fisco.bcos.sdk.v3.model.callback.TransactionCallback;
import org.fisco.bcos.sdk.v3.utils.Hex;
import org.fisco.solc.compiler.CompilationResult;
import org.fisco.solc.compiler.SolidityCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

public class ProxyContract {

    private static final Logger logger = LoggerFactory.getLogger(ProxyContract.class);

    private String chainPath;

    private BCOSAccount account;
    private BCOSConnection connection;

    private BCOSStubConfig bcosStubConfig;

    public ProxyContract(String chainPath, String accountName) throws Exception {
        this.chainPath = chainPath;
        BCOSStubConfigParser bcosStubConfigParser =
                new BCOSStubConfigParser(chainPath, "stub.toml");
        bcosStubConfig = bcosStubConfigParser.loadConfig();

        boolean isGMStub = bcosStubConfig.isGMStub();
        AbstractClientWrapper clientWrapper =
                ClientWrapperFactory.createClientWrapperInstance(bcosStubConfig);

        int cryptoType = isGMStub ? CryptoType.SM_TYPE : CryptoType.ECDSA_TYPE;
        String alg = isGMStub ? BCOSConstant.SM2P256V1 : BCOSConstant.SECP256K1;
        String stubType = bcosStubConfig.getType();
        BCOSBaseStubFactory bcosBaseStubFactory =
                new BCOSBaseStubFactory(cryptoType, alg, stubType);

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

    public CompilationResult.ContractMetadata getProxyContractAbiAndBin() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String proxyContractDir =
                chainPath + File.separator + BCOSConstant.BCOS_PROXY_NAME + File.separator;

        if (bcosStubConfig.isWASMStub()) {
            CompilationResult.ContractMetadata metadata = new CompilationResult.ContractMetadata();
            String proxyContractAbiFile = proxyContractDir + "we_cross_proxy.abi";
            String proxyContractBinFile = proxyContractDir + "we_cross_proxy.wasm";
            String proxyContractGmBinFile = proxyContractDir + "we_cross_proxy_gm.wasm";
            metadata.abi =
                    FileUtils.readFileToString(
                            resolver.getResource("classpath:" + proxyContractAbiFile).getFile(),
                            Charset.defaultCharset());

            if (bcosStubConfig.isGMStub()) {
                byte[] bytes =
                        FileUtils.readFileToByteArray(
                                resolver.getResource("classpath:" + proxyContractGmBinFile)
                                        .getFile());
                metadata.bin = Hex.toHexString(bytes);
            } else {
                byte[] bytes =
                        FileUtils.readFileToByteArray(
                                resolver.getResource("classpath:" + proxyContractBinFile)
                                        .getFile());
                metadata.bin = Hex.toHexString(bytes);
            }

            return metadata;
        } else {
            String proxyContractFile = proxyContractDir + "WeCrossProxy.sol";
            File solFile = resolver.getResource("classpath:" + proxyContractFile).getFile();

            /** First compile the contract source code */
            SolidityCompiler.Result res =
                    SolidityCompiler.compile(
                            solFile,
                            bcosStubConfig.isGMStub(),
                            true,
                            SolidityCompiler.Options.ABI,
                            SolidityCompiler.Options.BIN,
                            SolidityCompiler.Options.INTERFACE,
                            SolidityCompiler.Options.METADATA);

            if (res.isFailed()) {
                throw new RuntimeException(" compiling contract failed, " + res.getErrors());
            }

            CompilationResult.ContractMetadata metadata =
                    CompilationResult.parse(res.getOutput()).getContract("WeCrossProxy");

            return metadata;
        }
    }

    /** @return */
    public BFSInfo deployContractAndLinkBFS() throws Exception {

        logger.info("linkName: {}", BCOSConstant.BCOS_PROXY_NAME);

        AbstractClientWrapper clientWrapper = connection.getClientWrapper();
        Client client = clientWrapper.getClient();

        CompilationResult.ContractMetadata metadata = this.getProxyContractAbiAndBin();

        /** deploy the contract by sendTransaction */
        // groupId
        String groupID = connection.getProperties().get(BCOSConstant.BCOS_GROUP_ID);
        // chainId
        String chainID = connection.getProperties().get(BCOSConstant.BCOS_CHAIN_ID);

        BigInteger blockLimit = client.getBlockLimit();

        logger.info(
                " groupID: {}, chainID: {}, blockLimit: {}, accountAddress: {}, bin: {}, abi: {}",
                chainID,
                groupID,
                blockLimit,
                account.getCredentials().getAddress(),
                metadata.bin,
                metadata.abi);

        CryptoKeyPair credentials = account.getCredentials();

        int txAttribute = 0;
        String to = "";
        if (client.isWASM()) {
            txAttribute = LIQUID_CREATE | LIQUID_SCALE_CODEC;
            to = BCOSConstant.BCOS_PROXY_NAME + System.currentTimeMillis();
        }

        TxPair signedTransaction =
                TransactionBuilderJniObj.createSignedTransaction(
                        credentials.getJniKeyPair(),
                        groupID,
                        chainID,
                        to,
                        metadata.bin,
                        metadata.abi,
                        blockLimit.longValue(),
                        txAttribute);
        String signTx = signedTransaction.getSignedTx();

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
                                    TransactionReceiptStatus.getStatusMessage(
                                                    receipt.getStatus(), "Unknown error")
                                            .getMessage());
                            completableFuture.complete(null);
                        } else {
                            logger.info(
                                    " deploy contract success, contractAddress: {}",
                                    receipt.getContractAddress());
                            completableFuture.complete(receipt.getContractAddress());
                        }
                    }
                });

        String contractAddress = completableFuture.get(1000, TimeUnit.SECONDS);
        if (Objects.isNull(contractAddress)) {
            throw new Exception("Failed to deploy proxy contract.");
        }

        BFSService bfsService = new BFSService(client, credentials.generateKeyPair());
        RetCode retCode =
                bfsService.link(
                        BCOSConstant.BCOS_PROXY_NAME,
                        BCOSConstant.CONTRACT_DEFAULT_VERSION,
                        contractAddress,
                        metadata.abi);

        if (retCode.getCode() < PrecompiledRetCode.CODE_SUCCESS.getCode()) {
            throw new RuntimeException(" registerBfs failed, error message: " + retCode);
        }

        BFSInfo bfsInfo = new BFSInfo(BCOSConstant.CONTRACT_DEFAULT_VERSION, "link");
        bfsInfo.setAddress(contractAddress);
        bfsInfo.setAbi(metadata.abi);
        return bfsInfo;
    }

    public void deploy() throws Exception {
        if (!connection.hasProxyDeployed()) {
            System.out.println("Deploy WeCrossProxy to chain " + chainPath + " ...");

            deployContractAndLinkBFS();
            System.out.println(
                    "SUCCESS: WeCrossProxy: /apps/WeCrossProxy/latest has been deployed! chain: "
                            + chainPath);
        } else {
            System.out.println(
                    "SUCCESS: WeCrossProxy has already been deployed! chain: " + chainPath);
        }
    }

    public void upgrade() throws Exception {

        System.out.println("Upgrade WeCrossProxy to chain " + chainPath + " ...");

        deployContractAndLinkBFS();

        System.out.println(
                "SUCCESS: WeCrossProxy: /apps/WeCrossProxy/latest has been upgraded! chain: "
                        + chainPath);
    }
}
