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
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.apache.commons.io.FileUtils;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.contract.precompiled.bfs.BFSInfo;
import org.fisco.bcos.sdk.v3.contract.precompiled.bfs.BFSService;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.model.PrecompiledRetCode;
import org.fisco.bcos.sdk.v3.model.RetCode;
import org.fisco.bcos.sdk.v3.model.TransactionReceiptStatus;
import org.fisco.bcos.sdk.v3.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.v3.transaction.manager.TransactionProcessorFactory;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;
import org.fisco.bcos.sdk.v3.utils.Hex;
import org.fisco.solc.compiler.CompilationResult;
import org.fisco.solc.compiler.SolidityCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

public class HubContract {

    private static final Logger logger = LoggerFactory.getLogger(HubContract.class);

    private String chainPath;

    private BCOSAccount account;
    private BCOSConnection connection;

    private BCOSStubConfig bcosStubConfig;

    private AssembleTransactionProcessor assembleTransactionProcessor;

    public HubContract(String chainPath, String accountName) throws Exception {
        this.chainPath = chainPath;

        BCOSStubConfigParser bcosStubConfigParser =
                new BCOSStubConfigParser(chainPath, "stub.toml");
        bcosStubConfig = bcosStubConfigParser.loadConfig();

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
        this.assembleTransactionProcessor =
                TransactionProcessorFactory.createAssembleTransactionProcessor(
                        connection.getClientWrapper().getClient(), account.getCredentials());
        if (account == null) {
            throw new Exception("Account " + accountName + " not found");
        }

        if (connection == null) {
            throw new Exception("Init connection exception, please check log");
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

    public CompilationResult.ContractMetadata getHubContractAbiAndBin() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String hubContractDir =
                chainPath + File.separator + BCOSConstant.BCOS_HUB_NAME + File.separator;
        if (bcosStubConfig.isWASMStub()) {
            CompilationResult.ContractMetadata metadata = new CompilationResult.ContractMetadata();

            String hubContractAbiFile = hubContractDir + "we_cross_hub.abi";
            String hubContractBinFile = hubContractDir + "we_cross_hub.wasm";
            String hubContractGmBinFile = hubContractDir + "we_cross_hub_gm.wasm";
            metadata.abi =
                    FileUtils.readFileToString(
                            resolver.getResource("classpath:" + hubContractAbiFile).getFile(),
                            Charset.defaultCharset());

            if (bcosStubConfig.isGMStub()) {
                byte[] bytes =
                        FileUtils.readFileToByteArray(
                                resolver.getResource("classpath:" + hubContractGmBinFile)
                                        .getFile());
                metadata.bin = Hex.toHexString(bytes);
                ;
            } else {
                byte[] bytes =
                        FileUtils.readFileToByteArray(
                                resolver.getResource("classpath:" + hubContractBinFile).getFile());
                metadata.bin = Hex.toHexString(bytes);
            }

            return metadata;
        } else {
            String hubContractFile = hubContractDir + "WeCrossHub.sol";
            File solFile = resolver.getResource("classpath:" + hubContractFile).getFile();

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
                    CompilationResult.parse(res.getOutput())
                            .getContract(BCOSConstant.BCOS_HUB_NAME);

            return metadata;
        }
    }

    /** @return */
    public BFSInfo deployContractAndLinkBFS() throws Exception {

        logger.info("linkName: {}", BCOSConstant.BCOS_HUB_NAME);

        AbstractClientWrapper clientWrapper = connection.getClientWrapper();
        Client client = clientWrapper.getClient();

        CompilationResult.ContractMetadata metadata = this.getHubContractAbiAndBin();

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
        String to = "";
        if (client.isWASM()) {
            to = BCOSConstant.BCOS_HUB_NAME + System.currentTimeMillis();
        }

        TransactionResponse transactionResponse =
                assembleTransactionProcessor.deployAndGetResponse(
                        metadata.abi, metadata.bin, new ArrayList<>(), to);

        String contractAddress = null;
        if (!transactionResponse.getTransactionReceipt().isStatusOK()) {
            logger.error(
                    " deploy contract failed, error status: {}, error message: {} ",
                    transactionResponse.getTransactionReceipt().getStatus(),
                    TransactionReceiptStatus.getStatusMessage(
                                    transactionResponse.getTransactionReceipt().getStatus(),
                                    "Unknown error")
                            .getMessage());
        } else {
            contractAddress = transactionResponse.getTransactionReceipt().getContractAddress();
            logger.info(" deploy contract success, contractAddress: {}", contractAddress);
        }

        if (Objects.isNull(contractAddress)) {
            throw new Exception("Failed to deploy hub contract.");
        }
        BFSService bfsService = new BFSService(client, account.getCredentials().generateKeyPair());
        RetCode retCode =
                bfsService.link(
                        BCOSConstant.BCOS_HUB_NAME,
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
        if (!connection.hasHubDeployed()) {
            System.out.println("Deploy WeCrossHub to chain " + chainPath + " ...");

            deployContractAndLinkBFS();
            System.out.println(
                    "SUCCESS: WeCrossHub: /apps/WeCrossHub/latest has been deployed! chain: "
                            + chainPath);
        } else {
            System.out.println(
                    "SUCCESS: WeCrossHub has already been deployed! chain: " + chainPath);
        }
    }

    public void upgrade() throws Exception {
        System.out.println("Upgrade WeCrossHub to chain " + chainPath + " ...");

        deployContractAndLinkBFS();
        System.out.println(
                "SUCCESS: WeCrossHub: /apps/WeCrossHub/latest has been upgraded! chain: "
                        + chainPath);
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
