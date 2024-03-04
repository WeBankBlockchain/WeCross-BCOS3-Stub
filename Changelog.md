## v1.4.0

(2024-03-01)

**新增**

- stub增加获取区块的接口，增加跨链获取区块的功能 https://github.com/WeBankBlockchain/WeCross-BCOS3-Stub/pull/27
- 新增获取区块时返回交易详细信息的功能 https://github.com/WeBankBlockchain/WeCross-BCOS3-Stub/pull/29
- 获取的交易增加时间戳字段 https://github.com/WeBankBlockchain/WeCross-BCOS3-Stub/pull/26

**更改**

- 更新版本依赖，修复安全问题 https://github.com/WeBankBlockchain/WeCross-BCOS3-Stub/pull/30

**修复**

- 修复Proxy合约在事务为空时获取事件异常的问题 https://github.com/WeBankBlockchain/WeCross-BCOS3-Stub/pull/28
- 修复在计算默克尔证明时出现调用多次callback的问题 https://github.com/WeBankBlockchain/WeCross-BCOS3-Stub/pull/24

## v1.3.1

(2023-07-31)

**新增**

- 支持FISCO BCOS 3.+ WASM执行版本。

**修复**

- 修复FISCO BCOS 3.2.0之前版本节点的兼容性问题。

## v1.3.0

(2023-03-15)

**新增**

- 完全支持FISCO BCOS 3.+版本，功能对齐BCOS2 stub。

