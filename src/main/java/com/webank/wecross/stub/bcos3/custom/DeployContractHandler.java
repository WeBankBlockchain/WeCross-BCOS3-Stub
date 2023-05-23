package com.webank.wecross.stub.bcos3.custom;

import static org.fisco.bcos.sdk.v3.client.protocol.model.TransactionAttribute.LIQUID_CREATE;
import static org.fisco.bcos.sdk.v3.client.protocol.model.TransactionAttribute.LIQUID_SCALE_CODEC;

import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.BlockManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.Path;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.bcos3.AsyncBfsService;
import com.webank.wecross.stub.bcos3.BCOSConnection;
import com.webank.wecross.stub.bcos3.BCOSDriver;
import com.webank.wecross.stub.bcos3.account.BCOSAccount;
import com.webank.wecross.stub.bcos3.common.BCOSConstant;
import com.webank.wecross.stub.bcos3.common.BCOSStatusCode;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.fisco.bcos.sdk.jni.utilities.tx.TransactionBuilderJniObj;
import org.fisco.bcos.sdk.jni.utilities.tx.TxPair;
import org.fisco.bcos.sdk.v3.codec.wrapper.ABIDefinition;
import org.fisco.bcos.sdk.v3.codec.wrapper.ABIDefinitionFactory;
import org.fisco.bcos.sdk.v3.codec.wrapper.ABIObject;
import org.fisco.bcos.sdk.v3.codec.wrapper.ABIObjectFactory;
import org.fisco.bcos.sdk.v3.codec.wrapper.ContractABIDefinition;
import org.fisco.bcos.sdk.v3.codec.wrapper.ContractCodecJsonWrapper;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.model.TransactionReceiptStatus;
import org.fisco.bcos.sdk.v3.model.callback.TransactionCallback;
import org.fisco.bcos.sdk.v3.utils.Hex;
import org.fisco.solc.compiler.CompilationResult;
import org.fisco.solc.compiler.SolidityCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeployContractHandler implements CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(DeployContractHandler.class);

    private ContractCodecJsonWrapper contractCodecJsonWrapper = new ContractCodecJsonWrapper();

    public AsyncBfsService asyncBfsService;

    public AsyncBfsService getAsyncBfsService() {
        return asyncBfsService;
    }

    public void setAsyncBfsService(AsyncBfsService asyncBfsService) {
        this.asyncBfsService = asyncBfsService;
    }

    public ContractCodecJsonWrapper getContractCodecJsonWrapper() {
        return contractCodecJsonWrapper;
    }

    public void setContractCodecJsonWrapper(ContractCodecJsonWrapper contractCodecJsonWrapper) {
        this.contractCodecJsonWrapper = contractCodecJsonWrapper;
    }

    /**
     * @param path rule id
     * @param args command args
     * @param account if needs to sign
     * @param blockManager if needs to verify transaction
     * @param connection chain connection
     * @param callback
     */
    @Override
    public void handle(
            Path path,
            Object[] args,
            Account account,
            BlockManager blockManager,
            Connection connection,
            Driver.CustomCommandCallback callback,
            CryptoSuite cryptoSuite) {

        BCOSDriver driver = getAsyncBfsService().getBcosDriver();
        boolean isWasm = driver.isWasm();
        List<String> params = null;
        String abi;
        String bin;
        String className;
        String bfsName;

        if (isWasm) {
            if (Objects.isNull(args) || args.length < 4) {
                callback.onResponse(new Exception("incomplete args"), null);
                return;
            }
            bfsName = (String) args[0];
            abi = (String) args[1];
            className = (String) args[2];
            bin = (String) args[3];

            if (args.length > 4) {
                params = new ArrayList<>();
                for (int i = 4; i < args.length; ++i) {
                    params.add((String) args[i]);
                }
            }

        } else {
            if (Objects.isNull(args) || args.length < 3) {
                callback.onResponse(new Exception("incomplete args"), null);
                return;
            }
            bfsName = (String) args[0];
            String solidityContent = (String) args[1];
            className = (String) args[2];

            if (args.length > 3) {
                params = new ArrayList<>();
                for (int i = 3; i < args.length; ++i) {
                    params.add((String) args[i]);
                }
            }

            boolean sm = (cryptoSuite.getCryptoTypeConfig() == CryptoType.SM_TYPE);

            /* First compile the contract source code */
            CompilationResult.ContractMetadata metadata;
            try {

                File sourceFile = File.createTempFile("BCOSContract-", "-" + bfsName + ".sol");
                try (OutputStream outputStream = new FileOutputStream(sourceFile)) {
                    outputStream.write(solidityContent.getBytes());
                }

                // compile contract
                SolidityCompiler.Result res =
                        SolidityCompiler.compile(
                                sourceFile,
                                sm,
                                true,
                                SolidityCompiler.Options.ABI,
                                SolidityCompiler.Options.BIN,
                                SolidityCompiler.Options.INTERFACE,
                                SolidityCompiler.Options.METADATA);

                if (res.isFailed()) {
                    callback.onResponse(
                            new Exception("compiling contract failed, " + res.getErrors()),
                            res.getErrors());
                    return;
                }

                CompilationResult result = CompilationResult.parse(res.getOutput());
                metadata = result.getContract(className);
                abi = metadata.abi;
                bin = metadata.bin;
            } catch (Exception e) {
                logger.error("compiling contract failed, e: ", e);
                callback.onResponse(new Exception("compiling contract failed"), null);
                return;
            }
        }

        /* constructor params */
        ABIDefinitionFactory abiDefinitionFactory = new ABIDefinitionFactory(cryptoSuite);
        ContractABIDefinition contractABIDefinition = abiDefinitionFactory.loadABI(abi);
        ABIDefinition constructor = contractABIDefinition.getConstructor();
        /* check if solidity constructor needs arguments */
        byte[] paramsABI = new byte[0];
        if (!Objects.isNull(constructor)
                && !Objects.isNull(constructor.getInputs())
                && !constructor.getInputs().isEmpty()) {

            if (Objects.isNull(params)) {
                logger.error(" {} constructor needs arguments", className);
                callback.onResponse(
                        new Exception(className + " constructor needs arguments"), null);
                return;
            }

            ABIObject constructorABIObject = ABIObjectFactory.createInputObject(constructor);
            try {
                ABIObject abiObject = contractCodecJsonWrapper.encode(constructorABIObject, params);
                paramsABI = abiObject.encode(isWasm);
                if (logger.isTraceEnabled()) {
                    logger.trace(
                            " className: {}, params: {}, abi: {}",
                            className,
                            params.toArray(new String[0]),
                            Hex.toHexString(paramsABI));
                }
            } catch (Exception e) {
                logger.error(
                        "{} constructor arguments encode failed, params: {}, e: ",
                        className,
                        params.toArray(new String[0]),
                        e);
                callback.onResponse(
                        new Exception(
                                className
                                        + " constructor arguments encode failed, e: "
                                        + e.getMessage()),
                        null);
                return;
            }
        }

        if (logger.isTraceEnabled()) {
            logger.trace("deploy contract, name: {}, bin: {}, abi:{}", bfsName, bin, abi);
        }

        if (isWasm) {
            deployLiquidContractAndRegisterLink(
                    path,
                    bin + Hex.toHexString(paramsABI),
                    abi,
                    account,
                    connection,
                    driver,
                    blockManager,
                    (e, address) -> {
                        if (Objects.nonNull(e)) {
                            callback.onResponse(e, null);
                            return;
                        }

                        logger.info(" address: {}", address);
                        callback.onResponse(null, address);
                    });
        } else {
            deploySolContractAndRegisterLink(
                    path,
                    bin + Hex.toHexString(paramsABI),
                    abi,
                    account,
                    connection,
                    driver,
                    blockManager,
                    (e, address) -> {
                        if (Objects.nonNull(e)) {
                            callback.onResponse(e, null);
                            return;
                        }

                        logger.info(" address: {}", address);
                        callback.onResponse(null, address);
                    });
        }
    }

    private interface DeployContractCallback {
        void onResponse(Exception e, String address);
    }

    private void deploySolContractAndRegisterLink(
            Path path,
            String bin,
            String abi,
            Account account,
            Connection connection,
            Driver driver,
            BlockManager blockManager,
            DeployContractCallback callback) {

        Path proxyPath = new Path();
        proxyPath.setResource(BCOSConstant.BCOS_PROXY_NAME);

        TransactionRequest transactionRequest =
                new TransactionRequest(
                        BCOSConstant.PROXY_METHOD_DEPLOY,
                        Arrays.asList(
                                        path.toString(),
                                        ContractCodecJsonWrapper.HexEncodedDataPrefix + bin,
                                        abi)
                                .toArray(new String[0]));

        TransactionContext transactionContext =
                new TransactionContext(account, proxyPath, new ResourceInfo(), blockManager);

        driver.asyncSendTransaction(
                transactionContext,
                transactionRequest,
                true,
                connection,
                (exception, res) -> {
                    if (Objects.nonNull(exception)) {
                        logger.error(" deployAndRegisterLink e: ", exception);
                        callback.onResponse(exception, null);
                        return;
                    }

                    if (res.getErrorCode() != BCOSStatusCode.Success) {
                        logger.error(
                                " deployAndRegisterLink, error: {}, message: {}",
                                res.getErrorCode(),
                                res.getMessage());
                        callback.onResponse(new Exception(res.getMessage()), null);
                        return;
                    }

                    logger.info(
                            " deployAndRegisterLink successfully, name: {}, res: {} ",
                            path.getResource(),
                            res);

                    asyncBfsService.addAbiToCache(path.getResource(), abi);
                    callback.onResponse(null, res.getResult()[0]);
                });
    }

    private void deployLiquidContractAndRegisterLink(
            Path path,
            String bin,
            String abi,
            Account account,
            Connection connection,
            Driver driver,
            BlockManager blockManager,
            DeployContractCallback callback) {
        /** deploy the contract by sendTransaction */
        // groupId
        String groupID = connection.getProperties().get(BCOSConstant.BCOS_GROUP_ID);
        // chainId
        String chainID = connection.getProperties().get(BCOSConstant.BCOS_CHAIN_ID);

        try {
            BigInteger blockLimit =
                    ((BCOSConnection) connection).getClientWrapper().getBlockNumber();

            TxPair signedTransaction =
                    TransactionBuilderJniObj.createSignedTransaction(
                            ((BCOSAccount) account).getCredentials().getJniKeyPair(),
                            groupID,
                            chainID,
                            path.getResource() + System.currentTimeMillis(),
                            bin,
                            abi,
                            blockLimit.longValue(),
                            LIQUID_CREATE | LIQUID_SCALE_CODEC);
            String signTx = signedTransaction.getSignedTx();

            ((BCOSConnection) connection)
                    .getClientWrapper()
                    .sendTransaction(
                            signTx,
                            new TransactionCallback() {
                                @Override
                                public void onResponse(TransactionReceipt receipt) {
                                    if (!receipt.isStatusOK()) {
                                        logger.error(
                                                " deploy contract failed, error status: {}, error message: {} ",
                                                receipt.getStatus(),
                                                TransactionReceiptStatus.getStatusMessage(
                                                                receipt.getStatus(),
                                                                "Unknown error")
                                                        .getMessage());
                                        callback.onResponse(
                                                new Exception(receipt.getMessage()), null);
                                    } else {
                                        logger.info(
                                                " deploy contract success, contractAddress: {}",
                                                receipt.getContractAddress());
                                        asyncBfsService.linkBFSByProxy(
                                                path,
                                                receipt.getContractAddress(),
                                                abi,
                                                account,
                                                blockManager,
                                                connection,
                                                e -> {
                                                    if (Objects.nonNull(e)) {
                                                        logger.warn("registering abi failed", e);
                                                        callback.onResponse(e, null);
                                                        return;
                                                    }

                                                    logger.info(
                                                            " register bfs successfully path: {}, address: {}, abi: {}",
                                                            path,
                                                            receipt.getContractAddress(),
                                                            abi);

                                                    callback.onResponse(
                                                            null, receipt.getContractAddress());
                                                });
                                    }
                                }
                            });
        } catch (Exception e) {
            callback.onResponse(e, null);
        }
    }
}
