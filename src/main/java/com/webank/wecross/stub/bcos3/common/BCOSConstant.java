package com.webank.wecross.stub.bcos3.common;

import com.webank.wecross.stub.StubConstant;

/** The definition of the resource type in BCOS */
public interface BCOSConstant {
    String ADMIN_ACCOUNT = "admin";

    String BCOS3_SOL_DIR = "bcos3_sol";
    String BCOS3_LIQUID_DIR = "bcos3_liquid";
    String BCOS3_HUB_LIQUID_DIR = "we_cross_hub";
    String BCOS3_PROXY_LIQUID_DIR = "we_cross_proxy";
    String BCOS3_HUB_SOL_FILE = "WeCrossHub.sol";
    String BCOS3_PROXY_SOL_FILE = "WeCrossProxy.sol";
    String BCOS3_HUB_LIQUID_ABI_FILE = "we_cross_hub.abi";
    String BCOS3_HUB_LIQUID_WASM_FILE = "we_cross_hub.wasm";
    String BCOS3_HUB_LIQUID_GM_WASM_FILE = "we_cross_hub_gm.wasm";
    String BCOS3_PROXY_LIQUID_ABI_FILE = "we_cross_proxy.abi";
    String BCOS3_PROXY_LIQUID_WASM_FILE = "we_cross_proxy.wasm";
    String BCOS3_PROXY_LIQUID_GM_WASM_FILE = "we_cross_proxy_gm.wasm";

    String SECP256K1 = "secp256k1";
    String SM2P256V1 = "sm2p256v1";

    String RESOURCE_TYPE_BCOS_CONTRACT = "BCOS_CONTRACT";

    String WASM = "WASM";
    String GM = "GM";
    String ECDSA = "ECDSA";

    String BCOS3_ECDSA_EVM_STUB_TYPE = "BCOS3_ECDSA_EVM";
    String BCOS3_ECDSA_WASM_STUB_TYPE = "BCOS3_ECDSA_WASM";
    String BCOS3_GM_EVM_STUB_TYPE = "BCOS3_GM_EVM";
    String BCOS3_GM_WASM_STUB_TYPE = "BCOS3_GM_WASM";

    String BCOS_NODE_VERSION = "BCOS_PROPERTY_NODE_VERSION";
    String BCOS_GROUP_ID = "BCOS_PROPERTY_GROUP_ID";
    String BCOS_CHAIN_ID = "BCOS_PROPERTY_CHAIN_ID";
    String BCOS_STUB_TYPE = "BCOS_PROPERTY_STUB_TYPE";

    String BCOS_SEALER_LIST = "VERIFIER";
    int BCOS_NODE_ID_LENGTH = 128;

    String BCOS_PROXY_ABI = "WeCrossProxyABI";
    String BCOS_PROXY_NAME = StubConstant.PROXY_NAME;
    String BCOS_HUB_NAME = StubConstant.HUB_NAME;

    String CUSTOM_COMMAND_DEPLOY = "deploy";
    String CUSTOM_COMMAND_REGISTER = "register";
    String DEFAULT_ADDRESS = "0x1111111111111111111111111111111111111111";
    String PROXY_METHOD_DEPLOY = "deployContractWithRegisterBFS";
    String PROXY_METHOD_REGISTER = "linkBFS";
    String PROXY_METHOD_GETPATHS = "getPaths";
    String PROXY_METHOD_READLINK = "readlink";

    String CONTRACT_DEFAULT_VERSION = "latest";
}
