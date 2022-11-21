package com.webank.wecross.stub.bcos3;

import com.webank.wecross.stub.Stub;
import org.fisco.bcos.sdk.v3.model.CryptoType;

import static com.webank.wecross.stub.bcos3.BCOSBaseStubFactory.BCOS3_GM_EVM_STUB_TYPE;
import static com.webank.wecross.stub.bcos3.BCOSBaseStubFactory.BCOS3_GM_WASM_STUB_TYPE;

@Stub(BCOS3_GM_WASM_STUB_TYPE)
public class BCOS3GMWasmStubFactory extends BCOSBaseStubFactory {

    public BCOS3GMWasmStubFactory() {
        super(CryptoType.SM_TYPE, "sm2p256v1", BCOS3_GM_WASM_STUB_TYPE);
    }

    public static void main(String[] args) throws Exception {
        System.out.println(
                String.format("This is %s Stub Plugin. Please copy this file to router/plugin/", BCOS3_GM_WASM_STUB_TYPE));
        System.out.println("For deploy WeCrossProxy:");
        System.out.println(
                "    java -cp conf/:lib/*:plugin/* com.webank.wecross.stub.bcos.guomi.preparation.ProxyContractDeployment");
        System.out.println("For deploy WeCrossHub:");
        System.out.println(
                "    java -cp conf/:lib/*:plugin/* com.webank.wecross.stub.bcos.guomi.preparation.HubContractDeployment");
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
