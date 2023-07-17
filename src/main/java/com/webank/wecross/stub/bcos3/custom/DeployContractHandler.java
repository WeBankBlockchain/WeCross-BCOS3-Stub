package com.webank.wecross.stub.bcos3.custom;

import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.BlockManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.Path;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.bcos3.AsyncBfsService;
import com.webank.wecross.stub.bcos3.BCOSDriver;
import com.webank.wecross.stub.bcos3.common.BCOSConstant;
import com.webank.wecross.stub.bcos3.common.BCOSStatusCode;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.fisco.bcos.sdk.v3.codec.wrapper.ABIDefinition;
import org.fisco.bcos.sdk.v3.codec.wrapper.ABIDefinitionFactory;
import org.fisco.bcos.sdk.v3.codec.wrapper.ABIObject;
import org.fisco.bcos.sdk.v3.codec.wrapper.ABIObjectFactory;
import org.fisco.bcos.sdk.v3.codec.wrapper.ContractABIDefinition;
import org.fisco.bcos.sdk.v3.codec.wrapper.ContractCodecJsonWrapper;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.model.CryptoType;
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

        if (isWasm) {
            if (Objects.isNull(args) || args.length < 3) {
                callback.onResponse(new Exception("incomplete args"), null);
                return;
            }
            String bfsName = (String) args[0];
            String abi = (String) args[1];
            String bin = (String) args[2];
            List<String> params = null;

            if (args.length > 3) {
                params = new ArrayList<>();
                for (int i = 3; i < args.length; ++i) {
                    params.add((String) args[i]);
                }
            }

            if (logger.isTraceEnabled()) {
                logger.trace(
                        "deploy contract, name: {}, bin: {}, abi:{}, params:{}",
                        bfsName,
                        bin,
                        abi,
                        params);
            }

            deployLiquidContractAndRegisterLink(
                    path,
                    bin,
                    abi,
                    params,
                    account,
                    connection,
                    driver,
                    blockManager,
                    (e, address) -> {
                        if (Objects.nonNull(e)) {
                            logger.error("deploy failed ", e);
                            callback.onResponse(e, null);
                            return;
                        }

                        logger.info(" address: {}", address);
                        callback.onResponse(null, address);
                    });

        } else {
            if (Objects.isNull(args) || args.length < 3) {
                callback.onResponse(new Exception("incomplete args"), null);
                return;
            }
            String bfsName = (String) args[0];
            String solidityContent = (String) args[1];
            String className = (String) args[2];
            List<String> params = null;

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
            } catch (Exception e) {
                logger.error("compiling contract failed, e: ", e);
                callback.onResponse(new Exception("compiling contract failed"), null);
                return;
            }

            if (logger.isTraceEnabled()) {
                logger.trace(
                        "deploy contract, name: {}, bin: {}, abi:{}",
                        bfsName,
                        metadata.bin,
                        metadata.abi);
            }

            /* constructor params */
            ABIDefinitionFactory abiDefinitionFactory = new ABIDefinitionFactory(cryptoSuite);
            ContractABIDefinition contractABIDefinition =
                    abiDefinitionFactory.loadABI(metadata.abi);
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
                    ABIObject abiObject =
                            contractCodecJsonWrapper.encode(constructorABIObject, params);
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

            deploySolContractAndRegisterLink(
                    path,
                    metadata.bin + Hex.toHexString(paramsABI),
                    metadata.abi,
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
            List<String> params,
            Account account,
            Connection connection,
            Driver driver,
            BlockManager blockManager,
            DeployContractCallback callback) {
        try {
            List<String> requestParams = new ArrayList<>();
            String address = path.getResource() + System.currentTimeMillis();
            requestParams.addAll(Arrays.asList(address, bin, abi));
            requestParams.addAll(params);
            TransactionRequest transactionRequest =
                    new TransactionRequest(
                            BCOSConstant.CUSTOM_COMMAND_DEPLOY,
                            requestParams.toArray(new String[0]));

            TransactionContext transactionContext =
                    new TransactionContext(account, path, new ResourceInfo(), blockManager);

            driver.asyncSendTransaction(
                    transactionContext,
                    transactionRequest,
                    false,
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
                        asyncBfsService.linkBFSByProxy(
                                path,
                                address,
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
                                            path.getResource(),
                                            abi);

                                    callback.onResponse(null, path.getResource());
                                });
                    });
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            callback.onResponse(e, null);
        }
    }
}
