package com.webank.wecross.stub.bcos3;

import com.webank.wecross.stub.Stub;
import org.fisco.bcos.sdk.v3.model.CryptoType;

@Stub("BCOS3.0")
public class BCOSStubFactory extends BCOSBaseStubFactory {

    public BCOSStubFactory() {
        super(CryptoType.ECDSA_TYPE, "secp256k1", "BCOS3.0");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("This is BCOS3.0 Stub Plugin. Please copy this file to router/plugin/");
        System.out.println("For deploy WeCrossProxy:");
        System.out.println(
                "    java -cp conf/:lib/*:plugin/* com.webank.wecross.stub.bcos.normal.preparation.ProxyContractDeployment");
        System.out.println("For deploy WeCrossHub:");
        System.out.println(
                "    java -cp conf/:lib/*:plugin/* com.webank.wecross.stub.bcos.normal.preparation.HubContractDeployment");
        System.out.println("For chain performance test, please run the command for more info:");
        System.out.println(
                "    Pure:    java -cp conf/:lib/*:plugin/* "
                        + com.webank.wecross.stub.bcos3.performance.hellowecross.PerformanceTest
                                .class.getName());
        System.out.println(
                "    Proxy:   java -cp conf/:lib/*:plugin/* "
                        + com.webank.wecross.stub.bcos3.performance.hellowecross.proxy
                                .PerformanceTest.class.getName());
    }
}