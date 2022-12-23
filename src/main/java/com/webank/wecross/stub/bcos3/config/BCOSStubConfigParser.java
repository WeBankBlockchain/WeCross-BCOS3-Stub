package com.webank.wecross.stub.bcos3.config;

import com.moandjiezana.toml.Toml;
import com.webank.wecross.stub.bcos3.client.ClientDefaultConfig;
import com.webank.wecross.stub.bcos3.common.BCOSConstant;
import com.webank.wecross.stub.bcos3.common.BCOSToml;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Load and parser stub.toml configuration file for BCOS */
public class BCOSStubConfigParser extends AbstractBCOSConfigParser {

    private static final Logger logger = LoggerFactory.getLogger(BCOSStubConfigParser.class);

    private final String stubDir;

    public BCOSStubConfigParser(String configPath, String configName) {
        super(configPath + File.separator + configName);
        this.stubDir = configPath;
    }

    /**
     * parser configPath file and return BCOSConfig object
     *
     * @return
     * @throws IOException
     */
    public BCOSStubConfig loadConfig() throws IOException {

        BCOSToml bcosToml = new BCOSToml(getConfigPath());
        Toml toml = bcosToml.getToml();

        Map<String, Object> stubConfig = toml.toMap();

        // common
        Map<String, Object> commonConfigValue = (Map<String, Object>) stubConfig.get("common");
        requireItemNotNull(commonConfigValue, "common", getConfigPath());
        String stubName = (String) commonConfigValue.get("name");
        requireFieldNotNull(stubName, "common", "name", getConfigPath());
        String stubType = (String) commonConfigValue.get("type");
        requireFieldNotNull(stubType, "common", "type", getConfigPath());

        // chain
        Map<String, Object> chainConfigValue = (Map<String, Object>) stubConfig.get("chain");
        requireItemNotNull(chainConfigValue, "chain", getConfigPath());
        BCOSStubConfig.Chain chainConfig = getChainConfig(chainConfigValue);

        // service
        Map<String, Object> serviceConfigValue =
                (Map<String, Object>) stubConfig.get("service");
        requireItemNotNull(serviceConfigValue, "service", getConfigPath());
        BCOSStubConfig.Service serviceConfig =
                getServiceConfig(getConfigPath(), serviceConfigValue, stubType);

        // resources
        List<Map<String, String>> resourcesConfigValue =
                (List<Map<String, String>>) stubConfig.get("resources");
        if (resourcesConfigValue == null) {
            resourcesConfigValue = new ArrayList<>();
        }
        List<BCOSStubConfig.Resource> resourcesConfig =
                getBCOSResourceConfig(getConfigPath(), chainConfig, resourcesConfigValue);

        BCOSStubConfig bcosStubConfig = new BCOSStubConfig();
        bcosStubConfig.setType(stubType);
        bcosStubConfig.setChain(chainConfig);
        bcosStubConfig.setService(serviceConfig);
        bcosStubConfig.setResources(resourcesConfig);

        return bcosStubConfig;
    }

    public BCOSStubConfig.Chain getChainConfig(Map<String, Object> chainConfigValue) {
        // groupId field
        String groupId = (String) chainConfigValue.get("groupId");
        // chain field
        String chainId = (String) chainConfigValue.get("chainId");

        BCOSStubConfig.Chain chain = new BCOSStubConfig.Chain();
        chain.setChainID(
                StringUtils.isNotBlank(chainId) ? chainId : ClientDefaultConfig.DEFAULT_CHAIN_ID);
        chain.setGroupID(
                StringUtils.isNotBlank(groupId) ? groupId : ClientDefaultConfig.DEFAULT_GROUP_ID);

        return chain;
    }

    public BCOSStubConfig.Service getServiceConfig(
            String configFile, Map<String, Object> serviceConfigValue, String stubType) {
        // config
        BCOSStubConfig.Service serviceConfig = new BCOSStubConfig.Service();

        // caCert field
        String caCertPath = stubDir + File.separator + serviceConfigValue.get("caCert");
        requireFieldNotNull(caCertPath, "service", "caCert", configFile);
        // sslCert field
        String sslCert = stubDir + File.separator + serviceConfigValue.get("sslCert");
        requireFieldNotNull(sslCert, "service", "sslCert", configFile);
        // sslKey field
        String sslKey = stubDir + File.separator + serviceConfigValue.get("sslKey");
        requireFieldNotNull(sslKey, "service", "sslKey", configFile);
        serviceConfig.setCaCert(caCertPath);
        serviceConfig.setSslCert(sslCert);
        serviceConfig.setSslKey(sslKey);

        // stubType
        boolean isGmStub = StringUtils.containsIgnoreCase(stubType, BCOSConstant.GM);
        if (isGmStub) {
            String gmCaCert = stubDir + File.separator + serviceConfigValue.get("gmCaCert");
            requireFieldNotNull(gmCaCert, "service", "gmCaCert", configFile);

            String gmSslCert =
                    stubDir + File.separator + serviceConfigValue.get("gmSslCert");
            requireFieldNotNull(gmSslCert, "service", "gmSslCert", configFile);

            String gmSslKey = stubDir + File.separator + serviceConfigValue.get("gmSslKey");
            requireFieldNotNull(gmSslKey, "service", "gmSslKey", configFile);

            String gmEnSslCert =
                    stubDir + File.separator + serviceConfigValue.get("gmEnSslCert");
            requireFieldNotNull(gmEnSslCert, "service", "gmEnSslCert", configFile);

            String gmEnSslKey =
                    stubDir + File.separator + serviceConfigValue.get("gmEnSslKey");
            requireFieldNotNull(gmEnSslKey, "service", "gmEnSslKey", configFile);

            serviceConfig.setGmCaCert(gmCaCert);
            serviceConfig.setGmSslCert(gmSslCert);
            serviceConfig.setGmSslKey(gmSslKey);
            serviceConfig.setGmEnSslCert(gmEnSslCert);
            serviceConfig.setGmEnSslKey(gmEnSslKey);
        }

        // disableSsl
        Boolean disableSsl = (Boolean) serviceConfigValue.get("disableSsl");
        serviceConfig.setDisableSsl(
                Objects.isNull(disableSsl)
                        ? ClientDefaultConfig.DEFAULT_SERVICE_DISABLE_SSL
                        : disableSsl);

        // timeout field
        Long messageTimeout = (Long) serviceConfigValue.get("messageTimeout");
        serviceConfig.setMessageTimeout(
                Objects.isNull(messageTimeout)
                        ? ClientDefaultConfig.DEFAULT_SERVICE_TIMEOUT
                        : messageTimeout.intValue());

        // connectionsStr field
        List<String> connectionsStr =
                (List<String>) serviceConfigValue.get("connectionsStr");
        requireFieldNotNull(connectionsStr, "service", "connectionsStr", configFile);
        serviceConfig.setConnectionsStr(connectionsStr);

        // thread num
        Long threadPoolSize = (Long) serviceConfigValue.get("threadPoolSize");
        serviceConfig.setThreadPoolSize(
                Objects.isNull(threadPoolSize)
                        ? ClientDefaultConfig.DEFAULT_SERVICE_THREAD_NUMBER
                        : threadPoolSize.intValue());
        logger.debug("ServiceConfig: {}", serviceConfig);

        return serviceConfig;
    }

    public List<BCOSStubConfig.Resource> getBCOSResourceConfig(
            String configFile,
            BCOSStubConfig.Chain chain,
            List<Map<String, String>> resourcesConfigValue) {
        List<BCOSStubConfig.Resource> resourceList = new ArrayList<>();

        for (Map<String, String> stringStringMap : resourcesConfigValue) {
            // name
            String name = stringStringMap.get("name");
            requireFieldNotNull(name, "resources", "name", configFile);

            // type
            String type = stringStringMap.get("type");
            requireFieldNotNull(type, "resources", "type", configFile);
            // check type invalid
            if (!BCOSConstant.RESOURCE_TYPE_BCOS_CONTRACT.equals(type)) {
                logger.error(" unrecognized bcos resource type, name: {}, type: {}", name, type);
                continue;
            }

            // contractAddress
            String address = stringStringMap.get("contractAddress");
            requireFieldNotNull(address, "resources", "contractAddress", configFile);

            BCOSStubConfig.Resource resource = new BCOSStubConfig.Resource();
            resource.setName(name);
            resource.setType(type);
            resource.setValue(address);
            resourceList.add(resource);
        }

        logger.debug("resources: {}", resourceList);
        return resourceList;
    }
}
