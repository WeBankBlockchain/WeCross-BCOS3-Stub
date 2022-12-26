package com.webank.wecross.stub.bcos3.client;

import com.webank.wecross.exception.WeCrossException;
import com.webank.wecross.stub.bcos3.config.BCOSStubConfig;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.config.ConfigOption;
import org.fisco.bcos.sdk.v3.config.model.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class ClientUtility {

    private static final Logger logger = LoggerFactory.getLogger(ClientUtility.class);

    public static Client initClient(BCOSStubConfig bcosStubConfig) throws Exception {
        BCOSStubConfig.Service serviceConfig = bcosStubConfig.getService();
        // groupID
        String groupID = bcosStubConfig.getChain().getGroupID();

        // cryptoMaterial
        Map<String, Object> cryptoMaterial = buildCryptoMaterial(bcosStubConfig);

        // network
        Map<String, Object> network = new HashMap<>();
        network.put("peers", serviceConfig.getConnectionsStr());
        network.put("defaultGroup", ClientDefaultConfig.DEFAULT_GROUP_ID);
        network.put("messageTimeout", String.valueOf(serviceConfig.getMessageTimeout()));

        // threadPool
        Map<String, Object> threadPool = new HashMap<>();
        threadPool.put("threadPoolSize", String.valueOf(serviceConfig.getThreadPoolSize()));

        // configProperty
        ConfigProperty configProperty = new ConfigProperty();
        configProperty.setCryptoMaterial(cryptoMaterial);
        configProperty.setNetwork(network);
        configProperty.setThreadPool(threadPool);

        // configOption
        ConfigOption configOption = new ConfigOption(configProperty);

        // bcosSDK
        BcosSDK bcosSDK = new BcosSDK(configOption);
        return bcosSDK.getClient(groupID);
    }

    private static Map<String, Object> buildCryptoMaterial(BCOSStubConfig bcosStubConfig)
            throws WeCrossException, IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        BCOSStubConfig.Service serviceConfig = bcosStubConfig.getService();
        Map<String, Object> cryptoMaterial = new HashMap<>();
        cryptoMaterial.put("useSMCrypto", String.valueOf(bcosStubConfig.isGMStub()));
        cryptoMaterial.put("disableSsl", String.valueOf(serviceConfig.isDisableSsl()));
        if (serviceConfig.isDisableSsl()) {
            return cryptoMaterial;
        }
        if (bcosStubConfig.isGMStub()) {
            // gm
            checkCertExistAndPut(
                    resolver, cryptoMaterial, serviceConfig.getGmCaCert(), "caCert");
            checkCertExistAndPut(
                    resolver, cryptoMaterial, serviceConfig.getGmSslCert(), "sslCert");
            checkCertExistAndPut(
                    resolver, cryptoMaterial, serviceConfig.getGmSslKey(), "sslKey");
            checkCertExistAndPut(
                    resolver, cryptoMaterial, serviceConfig.getGmEnSslCert(), "enSslCert");
            checkCertExistAndPut(
                    resolver, cryptoMaterial, serviceConfig.getGmEnSslKey(), "enSslKey");
        } else {
            // not gm
            checkCertExistAndPut(
                    resolver, cryptoMaterial, serviceConfig.getCaCert(), "caCert");
            checkCertExistAndPut(
                    resolver, cryptoMaterial, serviceConfig.getSslCert(), "sslCert");
            checkCertExistAndPut(
                    resolver, cryptoMaterial, serviceConfig.getSslKey(), "sslKey");
        }
        return cryptoMaterial;
    }

    private static void checkCertExistAndPut(
            PathMatchingResourcePatternResolver resolver,
            Map<String, Object> cryptoMaterial,
            String certLocation,
            String key)
            throws WeCrossException, IOException {
        Resource certResource = resolver.getResource(certLocation);
        if (!certResource.exists() || !certResource.isFile()) {
            throw new WeCrossException(
                    WeCrossException.ErrorCode.DIR_NOT_EXISTS,
                    key + " does not exist, please check.");
        }
        cryptoMaterial.put(key, certResource.getFile().getPath());
    }
}
