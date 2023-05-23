#!/bin/bash
set -e
default_tag=v3.0.1
LOG_INFO() {
    local content=${1}
    echo -e "\033[32m ${content}\033[0m"
}

get_sed_cmd()
{
  local sed_cmd="sed -i"
  if [ "$(uname)" == "Darwin" ];then
        sed_cmd="sed -i .bkp"
  fi
  echo "$sed_cmd"
}

download_build_chain()
{
  tag=$(curl -sS "https://gitee.com/api/v5/repos/FISCO-BCOS/FISCO-BCOS/tags" | grep -oe "\"name\":\"v[2-9]*\.[0-9]*\.[0-9]*\"" | cut -d \" -f 4 | sort -V | tail -n 1)
  LOG_INFO "--- current tag: $tag"
  if [[ -z ${tag} ]]; then
    LOG_INFO "--- tag is empty, use default tag: ${default_tag}"
    tag="${default_tag}"
  fi
  curl -#LO "https://github.com/FISCO-BCOS/FISCO-BCOS/releases/download/${tag}/build_chain.sh" && chmod u+x build_chain.sh
}

prepare_environment()
{
    ## prepare resources for integration test
    pwd
    ls -a
    local node_type="${1}"
    # integration testing
    mkdir -p src/integTest/resources/chains/bcos
    mkdir src/integTest/resources/chains/bcos/WeCrossHub
    mkdir src/integTest/resources/chains/bcos/WeCrossProxy
    mkdir src/integTest/resources/chains/bcos/IntegBCOSAccount_GM
    mkdir src/integTest/resources/chains/bcos/IntegBCOSAccount
    cp -r nodes/127.0.0.1/sdk/* src/integTest/resources/chains/bcos
    cp src/test/resources/stub.toml src/integTest/resources/chains/bcos/
    cp -r src/test/resources/accounts/bcos/* src/integTest/resources/chains/bcos/IntegBCOSAccount/
    cp -r src/test/resources/accounts/gm_bcos/* src/integTest/resources/chains/bcos/IntegBCOSAccount_GM/

    mkdir -p src/integTest/resources/solidity
    cp -r src/test/resources/contract/* src/integTest/resources/solidity/
    cp src/main/resources/bcos3_sol/HelloWorld.sol src/integTest/resources/solidity/

    cp src/main/resources/bcos3_sol/WeCrossHub.sol src/integTest/resources/chains/bcos/WeCrossHub/
    cp src/main/resources/bcos3_liquid/we_cross_hub/we_cross_hub.abi src/integTest/resources/chains/bcos/WeCrossHub/
    cp src/main/resources/bcos3_liquid/we_cross_hub/we_cross_hub.wasm src/integTest/resources/chains/bcos/WeCrossHub/
    cp src/main/resources/bcos3_liquid/we_cross_hub/we_cross_hub_gm.wasm src/integTest/resources/chains/bcos/WeCrossHub/

    cp src/main/resources/bcos3_sol/WeCrossProxy.sol src/integTest/resources/chains/bcos/WeCrossProxy/
    cp src/main/resources/bcos3_liquid/we_cross_proxy/we_cross_proxy.abi src/integTest/resources/chains/bcos/WeCrossProxy/
    cp src/main/resources/bcos3_liquid/we_cross_proxy/we_cross_proxy.wasm src/integTest/resources/chains/bcos/WeCrossProxy/
    cp src/main/resources/bcos3_liquid/we_cross_proxy/we_cross_proxy_gm.wasm src/integTest/resources/chains/bcos/WeCrossProxy/

    if [ "${node_type}" == "sm" ];then
       sed_cmd=$(get_sed_cmd)
       $sed_cmd 's/BCOS3_ECDSA_EVM/BCOS3_GM_EVM/g' ./src/integTest/resources/chains/bcos/stub.toml
    fi
}

build_node()
{
  local node_type="${1}"
  if [ "${node_type}" == "sm" ];then
      bash -x build_chain.sh -l 127.0.0.1:4 -s
  else
      bash -x build_chain.sh -l 127.0.0.1:4
  fi
  ./nodes/127.0.0.1/fisco-bcos -v
  ./nodes/127.0.0.1/start_all.sh
}

check_standard_node()
{
  build_node
  prepare_environment
  ## run integration test
  bash gradlew test --info
  bash gradlew integTest --info
  bash nodes/127.0.0.1/stop_all.sh
  rm -rf nodes
}

check_sm_node()
{
  build_node sm
  prepare_environment sm
  ## run integration test
  bash gradlew test --info
  bash gradlew integTest --info
  bash nodes/127.0.0.1/stop_all.sh
  rm -rf nodes
}

check_basic()
{
# check code format
bash gradlew verifyGoogleJavaFormat
# build
bash gradlew build assemble
}

LOG_INFO "------ download_build_chain---------"
download_build_chain
LOG_INFO "------ check_basic---------"
check_basic
LOG_INFO "------ check_standard_node---------"
check_standard_node
LOG_INFO "------ check_sm_node---------"
check_sm_node

bash <(curl -s https://codecov.io/bash)
