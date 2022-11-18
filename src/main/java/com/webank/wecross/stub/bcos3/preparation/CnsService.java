package com.webank.wecross.stub.bcos3.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.stub.bcos3.client.AbstractClientWrapper;
import com.webank.wecross.stub.bcos3.common.BCOSConstant;
import com.webank.wecross.stub.bcos3.common.ObjectMapperFactory;
import com.webank.wecross.stub.bcos3.common.StatusCode;
import com.webank.wecross.stub.bcos3.contract.FunctionUtility;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.fisco.bcos.sdk.abi.FunctionEncoder;
import org.fisco.bcos.sdk.abi.TypeReference;
import org.fisco.bcos.sdk.abi.datatypes.Function;
import org.fisco.bcos.sdk.abi.datatypes.Type;
import org.fisco.bcos.sdk.abi.datatypes.Utf8String;
import org.fisco.bcos.sdk.client.protocol.response.Call;
import org.fisco.bcos.sdk.contract.precompiled.cns.CnsInfo;
import org.fisco.bcos.sdk.crypto.CryptoSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CnsService {

    private static final Logger logger = LoggerFactory.getLogger(CnsService.class);

    public static final int MAX_VERSION_LENGTH = 40;

    public static CnsInfo queryProxyCnsInfo(AbstractClientWrapper clientWrapper) {
        return queryCnsInfo(clientWrapper, BCOSConstant.BCOS_PROXY_NAME);
    }

    public static CnsInfo queryHubCnsInfo(AbstractClientWrapper clientWrapper) {
        return queryCnsInfo(clientWrapper, BCOSConstant.BCOS_HUB_NAME);
    }

    /** query cns to get address,abi of hub contract */
    private static CnsInfo queryCnsInfo(AbstractClientWrapper clientWrapper, String name) {
        /** function selectByName(string memory cnsName) public returns(string memory) */
        CryptoSuite cryptoSuite = clientWrapper.getCryptoSuite();
        FunctionEncoder functionEncoder = new FunctionEncoder(cryptoSuite);

        Function function =
                new Function(
                        BCOSConstant.CNS_METHOD_SELECTBYNAME,
                        Arrays.<Type>asList(new Utf8String(name)),
                        Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}));
        try {
            Call.CallOutput callOutput =
                    clientWrapper.call(
                            BCOSConstant.DEFAULT_ADDRESS,
                            BCOSConstant.CNS_PRECOMPILED_ADDRESS,
                            functionEncoder.encode(function));

            if (logger.isTraceEnabled()) {
                logger.trace(
                        "call result, status: {}, blockNumber: {}",
                        callOutput.getStatus(),
                        callOutput.getCurrentBlockNumber());
            }

            if (StatusCode.Success.equals(callOutput.getStatus())) {
                String cnsInfo = FunctionUtility.decodeOutputAsString(callOutput.getOutput());
                if (Objects.isNull(cnsInfo)) {
                    return null;
                }

                ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
                List<CnsInfo> infoList =
                        objectMapper.readValue(
                                cnsInfo,
                                objectMapper
                                        .getTypeFactory()
                                        .constructCollectionType(List.class, CnsInfo.class));

                if (Objects.isNull(infoList) || infoList.isEmpty()) {
                    logger.warn("Cns info empty.");
                    return null;
                } else {
                    int size = infoList.size();
                    CnsInfo hubCnsInfo = infoList.get(size - 1);
                    logger.info(
                            "{} cns info, name: {}, version: {}, address: {}, abi: {}",
                            name,
                            hubCnsInfo.getName(),
                            hubCnsInfo.getVersion(),
                            hubCnsInfo.getAddress(),
                            hubCnsInfo.getAbi());
                    return hubCnsInfo;
                }
            } else {
                logger.error(
                        "Unable query {} cns info, status: {}, message: {}",
                        name,
                        callOutput.getStatus(),
                        StatusCode.getStatusMessage(callOutput.getStatus()));
                return null;
            }
        } catch (Exception e) {
            logger.error("Query {} cns info e: ", name, e);
            return null;
        }
    }
}
