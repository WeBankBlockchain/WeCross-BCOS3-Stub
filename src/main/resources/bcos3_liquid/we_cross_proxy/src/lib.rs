#![cfg_attr(not(feature = "std"), no_std)]
#![allow(non_snake_case)]

use liquid::{storage, InOut};
use liquid_lang as liquid;
use liquid_prelude::{string::{String, ToString}, vec::Vec};
use liquid_primitives::types::Bytes;
use liquid_primitives::hash::hash;
use scale::{Encode};
use core::convert::TryFrom;


#[derive(InOut)]
pub struct BfsInfo {
    file_name: String,
    file_type: String,
    ext: Vec<String>,
}

#[liquid::interface(name = auto)]
mod bfs {
    use super::*;

    extern "liquid" {
        fn list(&self, absolutePath: String) -> (i32, Vec<BfsInfo>);
        // ISSUE： 预编译合约有方法名一样该如何处理？
        fn list(&self, absolutePath: String, offset: u256, limit: u256) -> (i256, Vec<BfsInfo>);
        fn mkdir(&mut self, absolutePath: String) -> i32;
        fn link(&mut self, absolutePath: String, _address: String, _abi: String) -> i32;
        fn link(&mut self, name: String, version: String, _address: String, _abi: String) -> i32;
        fn readlink(&self, absolutePath: String) -> Address;
    }
}

#[liquid::contract]
mod we_cross_proxy {
    use super::{bfs::*, *};

    const VERSION: &str = "v1.0.0";
    const XA_STATUS_PROCESSING: &str = "processing";
    const XA_STATUS_COMMITTED: &str = "committed";
    const XA_STATUS_ROLLEDBACK: &str = "rolledback";
    const REVERT_FLAG: &str = "_revert";
    const NULL_FLAG: &str = "null";
    const SUCCESS_FLAG: &str = "success";
    const SEPARATOR: &str = ".";
    const BFS_APPS: &str = "/apps/";
    const DEFAULT_VERSION: &str = "latest";

    #[derive(InOut, Clone)]
    #[cfg_attr(feature = "std", derive(Debug, PartialEq, Eq))]
    pub struct XATransactionStep {
        accountIdentity: String,
        timestamp: u256,
        path: String,
        contractAddress: Address,
        func: String,
        args: Bytes,
    }

    #[derive(InOut, Clone)]
    #[cfg_attr(feature = "std", derive(Debug, PartialEq, Eq))]
    pub struct XATransaction {
        accountIdentity: String,
        paths: Vec<String>,
        contractAddresses: Vec<Address>,
        status: String,
        startTimestamp: u256,
        commitTimestamp: u256,
        rollbackTimestamp: u256,
        seqs: Vec<u256>,
        stepNum: u256,
    }

    #[derive(InOut, Clone)]
    #[cfg_attr(feature = "std", derive(Debug, PartialEq, Eq))]
    pub struct ContractStatus {
        locked: bool,
        xaTransactionID: String,
    }

    #[derive(InOut, Clone)]
    #[cfg_attr(feature = "std", derive(Debug, PartialEq, Eq))]
    pub struct Transaction {
        existed: bool,
        result: Bytes,
    }

    /// Defines the state variables of your contract.
    #[liquid(storage)]
    struct WeCrossProxy {
        bfs: storage::Value<Bfs>,

        head: storage::Value<u256>,
        tail: storage::Value<u256>,
        pathCache: storage::Vec<String>,
        xaTransactionIDs: storage::Vec<String>,
        lockedContracts: storage::Mapping<Address, ContractStatus>,
        xaTransactions: storage::Mapping<String, XATransaction>,
        xaTransactionSteps: storage::Mapping<String, XATransactionStep>,
        transactions: storage::Mapping<String, Transaction>,
    }

    /// Defines the methods of your contract.
    #[liquid(methods)]
    impl WeCrossProxy {
        pub fn new(&mut self) {
            self.bfs.initialize(Bfs::at("/sys/bfs".parse().unwrap()));

            self.head.initialize(0.into());
            self.tail.initialize(0.into());
            self.pathCache.initialize();
            self.xaTransactionIDs.initialize();
            self.lockedContracts.initialize();
            self.xaTransactions.initialize();
            self.xaTransactionSteps.initialize();
            self.transactions.initialize();
        }

        pub fn getVersion(&self) -> String {
            return VERSION.to_string();
        }

        pub fn addPath(&mut self, _path: String) {
            self.pathCache.push(_path);
        }

        pub fn getPaths(&self) -> Vec<String> {
            // ISSUE： 如何返回storage::Vec
            let mut vec: Vec<String> = Vec::new();
            for path in self.pathCache.iter() {
                vec.push(path.clone());
            }

            return vec;
        }

        pub fn deletePathList(&mut self) {
            // ISSUE： 如何清空storage::Vec
            while !self.pathCache.is_empty() {
                self.pathCache.pop();
            }
        }

        pub fn deployContract(&mut self, _bin: Bytes) -> Address {
            // ISSUE： liquid contract doesn't support to deploy contract！
            return Address::empty();
        }

        pub fn deployContractWithRegisterBFS (&mut self, 
            _path: String,
            _bin: Bytes,
            _abi: String
        ) -> Address {
            // ISSUE： liquid contract doesn't support to deploy contract！
            return Address::empty();
        }

        pub fn linkBFS(&mut self, 
            _path: String, 
            _addr: String,
            _abi: String
        ) {    
            let name = self.getNameByPath(&_path);
            let addr = self.getAddressByName(&name, false);

            if addr != Address::empty() && self.lockedContracts[&addr].locked {
                liquid::env::revert(&(name.clone() + " is locked by unfinished xa transaction: " + &(self.lockedContracts[&addr].xaTransactionID)));
            }
            
            let ret = self.bfs.link(name.clone(), DEFAULT_VERSION.to_string(), _addr, _abi);
            if 0 != ret.unwrap() {
                liquid::env::revert(&(name.clone() + ":" + DEFAULT_VERSION + " unable link to BFS, error: " + &ret.unwrap().to_string()));
            }
            
            self.pathCache.push(_path);
        }

        pub fn readlink(&self, name: String) -> (String, String, String) {
            let (ret, bfsList) = self.bfs.list(self.nameToBfsPath(&name)).unwrap();
            if ret < 0 || 
                bfsList.len() < 1 ||
                bfsList.first().unwrap().file_type != "link" ||
                bfsList.first().unwrap().ext.len() < 2 {
                
                return ("".to_string(), "".to_string(), "".to_string());
            }

            let bfsInfo = bfsList.first().unwrap();
            return (bfsInfo.file_name.clone(), bfsInfo.ext[0].to_string(), bfsInfo.ext[1].to_string());
        }

        pub fn constantCall(
            &mut self,
            _XATransactionID: String,
            _path: String,
            _func: String,
            _args: Bytes
        ) -> Bytes {
            let addr = self.getAddressByPath(&_path);

            if !self.isExistedXATransaction(&_XATransactionID) {
                liquid::env::revert(&"xa transaction not found".to_string());
            }

            if self.lockedContracts[&addr].xaTransactionID != _XATransactionID {
                liquid::env::revert(&(_path + " is unregistered in xa transaction: " + &_XATransactionID));
            }

            return self.callContract_internal_with_func(&addr, &_func, &_args);
        }

        pub fn constantCallWithoutXa(
            &mut self,
            _name: String,
            _argsWithMethodId: Bytes
        ) -> Bytes {
            let addr = self.getAddressByName(&_name, true);

            if self.lockedContracts[&addr].locked {
                liquid::env::revert(&("resource is locked by unfinished xa transaction: ".to_string() + &(self.lockedContracts[&addr].xaTransactionID)));
            }

            return self.callContract_internal_with_data(&addr, &_argsWithMethodId);
        }

        pub fn sendTransaction(
            &mut self,
            _uid: String,
            _XATransactionID: String,
            _XATransactionSeq: u256,
            _path: String,
            _func: String,
            _args: Bytes,
        ) -> Bytes {

            if self.transactions[&_uid].existed {
                return self.transactions[&_uid].result.clone();
            }

            let addr = self.getAddressByPath(&_path);

            if !self.isExistedXATransaction(&_XATransactionID) {
                liquid::env::revert(&"xa transaction not found".to_string());
            }

            if self.xaTransactions[&_XATransactionID].status == XA_STATUS_COMMITTED {
                liquid::env::revert(&"xa transaction has been committed".to_string());
            }

            if self.xaTransactions[&_XATransactionID].status == XA_STATUS_ROLLEDBACK {
                liquid::env::revert(&"xa transaction has been rolledback".to_string());
            }

            if self.lockedContracts[&addr].xaTransactionID != _XATransactionID {
                liquid::env::revert(&(_path.clone() + " is unregistered in xa transaction " + &_XATransactionID));
            }

            if !self.isValidXATransactionSep(&_XATransactionID, &_XATransactionSeq) {
                liquid::env::revert(&"seq should be greater than before".to_string());
            }

            let key = self.getXATransactionStepKey(&_XATransactionID, &_XATransactionSeq);
            self.xaTransactionSteps[&key] = XATransactionStep {
                accountIdentity: self.env().get_tx_origin().to_string(),
                timestamp: (self.env().now() / 1000).into(),
                path: _path,
                contractAddress: addr.clone(),
                func: _func.clone(),
                args: _args.clone(),
            };

            // recode seq
            let num = self.xaTransactions[&_XATransactionID].stepNum.clone();
            let index = self.u256_to_usize(&num);
            self.xaTransactions[&_XATransactionID].seqs[index] = _XATransactionSeq;
            self.xaTransactions[&_XATransactionID].stepNum = num + 1.into();

            let result = self.callContract_internal_with_func(&addr, &_func, &_args);

            self.transactions[&_uid] = Transaction{
                existed: true, 
                result: result.clone(),
            };

            return result;
        }

        pub fn sendTransactionWithoutXa(
            &mut self,
            _uid: String,
            _name: String,
            _argsWithMethodId: Bytes
        ) -> Bytes {

            if self.transactions[&_uid].existed {
                return self.transactions[&_uid].result.clone();
            }

            let addr = self.getAddressByName(&_name, true);

            if self.lockedContracts[&addr].locked {
                liquid::env::revert(&(_name + " is locked by unfinished xa transaction: " + &self.lockedContracts[&addr].xaTransactionID));
            }

            let result = self.callContract_internal_with_data(&addr, &_argsWithMethodId);

            self.transactions[&_uid] = Transaction{
                existed: true, 
                result: result.clone(),
            };

            return result;
        }

        pub fn startXATransaction(
            &mut self,
            _xaTransactionID: String,
            _selfPaths: Vec<String>,
            _otherPaths: Vec<String>
        ) -> String {

            if self.isExistedXATransaction(&_xaTransactionID) {
                liquid::env::revert(&("xa transaction ".to_string() + &_xaTransactionID + " already exists"));
            }

            let mut contracts :Vec<Address> = Vec::new();
            let mut allPaths :Vec<String> = Vec::new();

            for self_path in _selfPaths.iter() {
                let addr = self.getAddressByPath(self_path);
                contracts.push(addr.clone());

                if self.lockedContracts[&addr].locked {
                    liquid::env::revert(&(self_path.to_string() + " is locked by unfinished xa transaction: " + &(self.lockedContracts[&addr].xaTransactionID)));
                }

                self.lockedContracts[&addr].locked = true;
                self.lockedContracts[&addr].xaTransactionID = _xaTransactionID.clone();
                allPaths.push(self_path.to_string());
            }

            for other_path in _otherPaths.iter() {
                allPaths.push(other_path.to_string());
            }

            // recode xa transaction
            self.xaTransactions[&_xaTransactionID] = XATransaction{
                accountIdentity: self.env().get_tx_origin().to_string(),
                paths: allPaths,
                contractAddresses: contracts,
                status: XA_STATUS_PROCESSING.to_string(),
                startTimestamp: (self.env().now() / 1000).into(),
                commitTimestamp: 0.into(),
                rollbackTimestamp: 0.into(),
                seqs: Vec::new(),
                stepNum: 0.into(),
            };

            self.addXATransaction(&_xaTransactionID);

            return SUCCESS_FLAG.to_string();
        }

        pub fn commitXATransaction(
            &mut self,
            _xaTransactionID: String
        ) -> String {
            if !self.isExistedXATransaction(&_xaTransactionID) {
                liquid::env::revert(&"xa transaction not found".to_string());
            }

            if self.xaTransactions[&_xaTransactionID].status == XA_STATUS_COMMITTED {
                liquid::env::revert(&"xa transaction has been committed".to_string());
            }

            if self.xaTransactions[&_xaTransactionID].status == XA_STATUS_ROLLEDBACK {
                liquid::env::revert(&"xa transaction has been rolledback".to_string());
            }

            self.xaTransactions[&_xaTransactionID].commitTimestamp = (self.env().now() / 1000).into();
            self.xaTransactions[&_xaTransactionID].status = XA_STATUS_COMMITTED.to_string();

            self.deleteLockedContracts(&_xaTransactionID);
            self.head += 1.into();

            return SUCCESS_FLAG.to_string();
        }

        pub fn rollbackXATransaction(
            &mut self,
            _xaTransactionID: String
        ) -> String {
            let mut result = SUCCESS_FLAG.to_string();
            if !self.isExistedXATransaction(&_xaTransactionID) {
                liquid::env::revert(&"xa transaction not found".to_string());
            }

            if self.xaTransactions[&_xaTransactionID].status == XA_STATUS_COMMITTED {
                liquid::env::revert(&"xa transaction has been committed".to_string());
            }

            if self.xaTransactions[&_xaTransactionID].status == XA_STATUS_ROLLEDBACK {
                liquid::env::revert(&"xa transaction has been rolledback".to_string());
            }

            let mut message = "warning:".to_string();
            let mut i = self.xaTransactions[&_xaTransactionID].stepNum.clone();
            while i > 0.into() {
                let seq = &self.xaTransactions[&_xaTransactionID].seqs[self.u256_to_usize(&(i.clone() - 1.into()))];
                let key = self.getXATransactionStepKey(&_xaTransactionID, seq);
                let func = &self.xaTransactionSteps[&key].func;
                let contractAddress = &self.xaTransactionSteps[&key].contractAddress;
                let args = &self.xaTransactionSteps[&key].args;

                // ISSUE: hash 是否等价于abi.encodeWithSignature 取前4个
                let data = hash(self.getRevertFunc(func, REVERT_FLAG).as_bytes()).to_vec();
                data.extend(&(args).encode());

                // ISSUE: call的使用是否正确
                match liquid::env::call::<Bytes>(contractAddress, &data) {
                    Ok(_) => (),
                    Err(_) => {
                        message =  message + " revert \"" + func + "\" failed.'";
                        result = message.clone();
                    }
                }

                i = i - 1.into();
            }

            self.xaTransactions[&_xaTransactionID].rollbackTimestamp =  (self.env().now() / 1000).into();
            self.xaTransactions[&_xaTransactionID].status = XA_STATUS_ROLLEDBACK.to_string();
            self.deleteLockedContracts(&_xaTransactionID);
            return result;
        }

        pub fn getXATransactionNumber(&self) -> String {
            if self.xaTransactionIDs.len() == 0 {
                return "0".to_string();
            } else {
                return self.xaTransactionIDs.len().to_string();
            }
        }

        pub fn listXATransactions(
            &self,
            _index: String,
            _size:u256
        ) -> String {
            let len = self.xaTransactionIDs.len();
            let index = if "-1".to_string() == _index {len - 1} else {_index.parse().unwrap()};

            if len == 0 || len <= index {
                return "{\"tota\":0,\"xaTransactions\":[]}".to_string();
            }

            let mut jsonStr = "[".to_string();
            let mut i = 0;
            let end: u256 = _size.clone() - 1.into();
            while u256::from(i) < end && (index - i) > 0 {
                let xaTransactionID = self.xaTransactionIDs[index - i].clone();
                
                jsonStr = jsonStr + 
                            r#"{"xaTransactionID":""# +
                            &xaTransactionID +
                            r#"","accountIdentity":""# +
                            &self.xaTransactions[&xaTransactionID].accountIdentity + 
                            r#"","status":""# +
                            &self.xaTransactions[&xaTransactionID].status +
                            r#"","paths":"# +
                            &self.pathsToJson(&xaTransactionID) +
                            r#","timestamp":"# +
                            &self.xaTransactions[&xaTransactionID].startTimestamp.to_string() +
                            "},";
                i += 1;
            }

            let lastIndex = if u256::from(index + 1) >= _size {u256::from(index + 1) - _size} else {0.into()};
            let xaTransactionID = self.xaTransactionIDs[self.u256_to_u32(&lastIndex)].clone();
    
            jsonStr = jsonStr + 
                        r#"{"xaTransactionID":""# +
                        &xaTransactionID +
                        r#"","accountIdentity":""# +
                        &self.xaTransactions[&xaTransactionID].accountIdentity + 
                        r#"","status":""# +
                        &self.xaTransactions[&xaTransactionID].status +
                        r#"","paths":"# +
                        &self.pathsToJson(&xaTransactionID) +
                        r#","timestamp":"# +
                        &self.xaTransactions[&xaTransactionID].startTimestamp.to_string() +
                        "}]";

            jsonStr = r#"{"total":"#.to_string() +
                        &len.to_string() + 
                        r#","xaTransactions":"# +
                        &jsonStr + 
                        "}";

            return jsonStr;
        }

        pub fn getXATransaction(
            &self,
            _xaTransactionID: String
        ) -> String {
            if !self.isExistedXATransaction(&_xaTransactionID) {
                liquid::env::revert(&"xa transaction not found".to_string());
            }

            let jsonStr = r#"{"xaTransactionID":""#.to_string() +
                            &_xaTransactionID +
                            r#"","accountIdentity":""# +
                            &self.xaTransactions[&_xaTransactionID].accountIdentity +
                            r#"","status":""# +
                            &self.xaTransactions[&_xaTransactionID].status +
                            r#"","paths":"# +
                            &self.pathsToJson(&_xaTransactionID) +
                            r#","startTimestamp":"# +
                            &self.xaTransactions[&_xaTransactionID].startTimestamp.to_string() +
                            r#","commitTimestamp":"# +
                            &self.xaTransactions[&_xaTransactionID].commitTimestamp.to_string() +
                            r#","rollbackTimestamp":"# +
                            &self.xaTransactions[&_xaTransactionID].rollbackTimestamp.to_string() +
                            r#","xaTransactionSteps":"# +
                            &self.xaTransactionStepArrayToJson(
                                &_xaTransactionID,
                                &self.xaTransactions[&_xaTransactionID].seqs,
                                &self.xaTransactions[&_xaTransactionID].stepNum
                            ) +
                            "}";

            return jsonStr;
        }

        pub fn getLatestXATransaction(&self) -> String {
            if self.head == self.tail {
                return "{}".to_string();
            } else {
                let xaTransactionID = self.xaTransactionIDs[self.u256_to_u32(&self.head)].clone();
                return self.getXATransaction(xaTransactionID);
            }
        }

        pub fn rollbackAndDeleteXATransactionTask(
            &mut self,
            _xaTransactionID: String
        ) -> String {
            self.rollbackXATransaction(_xaTransactionID.clone());
            return self.deleteXATransactionTask(&_xaTransactionID);
        }

        pub fn getLatestXATransactionID(&self) -> String {
            if self.head == self.tail {
                return NULL_FLAG.to_string();
            } else {
                return self.xaTransactionIDs[self.u256_to_u32(&self.head)].clone();
            }
        }

        pub fn getXATransactionState(
            &self,
            _path: String
        ) -> String {
            let addr = self.getAddressByPath(&_path);
            if !self.lockedContracts[&addr].locked {
                return NULL_FLAG.to_string();
            } else {
                let xaTransactionID = self.lockedContracts[&addr].xaTransactionID.clone();
                let index = self.xaTransactions[&xaTransactionID].stepNum.clone();
                let seq = if index == 0.into() {0.into()} else {self.xaTransactions[&xaTransactionID].seqs[self.u256_to_usize(&(index - 1.into()))].clone()};
                
                return xaTransactionID + &" " + &seq.to_string();
            }
        }

        fn addXATransaction(
            &mut self,
            _xaTransactionID: &String
        ) {
            self.tail += 1.into();
            self.xaTransactionIDs.push(_xaTransactionID.clone());
        }

        fn deleteXATransactionTask(
            &mut self,
            _xaTransactionID: &String
        ) -> String {
            if self.head == self.tail {
                liquid::env::revert(&"delete nonexistent xa transaction".to_string());
            }
    
            if &self.xaTransactionIDs[self.u256_to_u32(&self.head)] != _xaTransactionID {
                liquid::env::revert(&"delete unmatched xa transaction".to_string());
            }

            self.head += 1.into();
            return SUCCESS_FLAG.to_string();
        }

        fn callContract_internal_with_func(
            &mut self,
            _contractAddress: &Address,
            _sig: &String,
            _args: &Bytes
        ) -> Bytes {
            // TODO 取前4个
            let mut data: Vec<u8> = hash(_sig.as_bytes()).to_vec();
            data.extend(&(_args).encode());
            match liquid::env::call::<Bytes>(&_contractAddress, &data) {
                Ok(ret) => ret,
                Err(_) => {
                    liquid::env::revert(&("call contract failed: ".to_string() + &_contractAddress.to_string()));
                    // ISSUE: EnvError to string
                    // liquid::env::revert(&err.to_string());
                    Bytes::new()
                }
            }
        }

        fn callContract_internal_with_data(
            &mut self,
            _contractAddress: &Address,
            _argsWithMethodId: &Bytes
        ) -> Bytes {
            match liquid::env::call::<Bytes>(&_contractAddress, &_argsWithMethodId) {
                Ok(ret) => ret,
                Err(_) => {
                    liquid::env::revert(&("call contract failed: ".to_string() + &_contractAddress.to_string()));
                    // ISSUE: EnvError to string
                    // liquid::env::revert(&err.to_string());
                    Bytes::new()
                }
            }
        }

        fn getAddressByName(
            &self,
            _name: &String,
            revertNotExist: bool
        ) -> Address {
            let _address = self.bfs.readlink(self.nameToBfsPath(_name)).unwrap();

            if _address == Address::empty() {
                if revertNotExist {
                    liquid::env::revert(&"the name's address not exist.".to_string());
                }
            }

            return _address;
        }

        fn getAddressByPath(
            &self,
            _path: &String
        ) -> Address {
            let name = self.getNameByPath(&_path);
            return self.getAddressByName(&name, true);    
        }

        fn getNameByPath(
            &self,
            _path: &String
        ) -> String {
            let ret: Vec<&str> = _path.split(SEPARATOR).collect();
            return ret.last().unwrap().to_string();
        }

        fn pathsToJson(
            &self,
            _transactionID: &String
        ) -> String {
            let len = self.xaTransactions[_transactionID].paths.len();
            let mut paths = "[\"".to_string() + &self.xaTransactions[_transactionID].paths[0] + "\"";
            let mut i = 1;
            while i < len {
                paths = paths + ",\"" + &self.xaTransactions[_transactionID].paths[i] + "\"";
                i += 1;
            }

            paths = paths + "]";
            return paths;
        }

        fn xaTransactionStepArrayToJson(
            &self,
            _transactionID: &String,
            _seqs: &Vec<u256>,
            _len: &u256
        ) -> String {
            if _len.clone() == u256::from(0) {
                return "[]".to_string();
            }

            let mut result = "[".to_string() + &self.xatransactionStepToJson(
                &self.xaTransactionSteps[&self.getXATransactionStepKey(&_transactionID, &_seqs[0])],
                &_seqs[0]);

            let mut i = 1;
            while u256::from(i) < _len.clone() {
                result = result + "," + &self.xatransactionStepToJson(
                    &self.xaTransactionSteps[&self.getXATransactionStepKey(&_transactionID, &_seqs[i])],
                    &_seqs[i]);
    
                i += 1;
            }

            result = result + "]";
            return result;
        }

        fn xatransactionStepToJson(
            &self,
            _xaTransactionStep: &XATransactionStep,
            _XATransactionSeq: &u256
        ) -> String {
            let jsonStr = r#"{"xaTransactionSeq":"#.to_string() + 
                            &_XATransactionSeq.to_string() +
                            r#","accountIdentity":""# + 
                            &_xaTransactionStep.accountIdentity +
                            r#"","path":""# + 
                            &_xaTransactionStep.path + 
                            r#"","timestamp":"# + 
                            &_xaTransactionStep.timestamp.to_string() +
                            r#","method":""# + 
                            &self.getMethodFromFunc(&_xaTransactionStep.func) + 
                            r#"","args":""# + 
                            &self.bytesToHexString(&_xaTransactionStep.args) +
                            "\"}";
            return jsonStr;
        }

        fn isExistedXATransaction(
            &self,
            _xaTransactionID: &String
        ) -> bool {
            return self.xaTransactions[_xaTransactionID].startTimestamp != 0.into();
        }

        fn isValidXATransactionSep(
            &self,
            _xaTransactionID: &String,
            _XATransactionSeq: &u256
        ) -> bool {
            let index = self.xaTransactions[_xaTransactionID].stepNum.clone();
            return index == 0.into() || _XATransactionSeq > &self.xaTransactions[_xaTransactionID].seqs[self.u256_to_usize(&(index - 1.into()))];
        }

        fn deleteLockedContracts(
            &mut self,
            _xaTransactionID: &String,
        ) {
            let len = self.xaTransactions[_xaTransactionID].contractAddresses.len();
            let mut i = 0;
            while i < len {
                let contractAddress = self.xaTransactions[_xaTransactionID].contractAddresses[i].clone();
                self.lockedContracts.remove(&contractAddress);
                i += 1;
            }
        }

        fn getRevertFunc(
            &self,
            _func: &String,
            _revertFlag: &str
        ) -> String {
            let index = _func.find("(").unwrap();
            let mut revert_fun = _func.to_string();
            revert_fun.insert_str(index, _revertFlag);
            return revert_fun;
        }

        fn getMethodFromFunc(
            &self,
            _func: &String
        ) -> String {
            let index = _func.find("(").unwrap();
            return _func.split_at(index).0.to_string();
        }

        fn getXATransactionStepKey(
            &self,
            _transactionID: &String,
            _transactionSeq: &u256
        ) -> String {
            return _transactionID.to_string() + &_transactionSeq.to_string();
        }

        fn bytesToHexString(
            &self,
            _bts: &Bytes
        ) -> String {
            // 没有0x ?
            return liquid_lang::bytes_to_hex(_bts);
        }

        fn nameToBfsPath(
            &self,
            _name: &String
        ) -> String {
            return BFS_APPS.to_string() + _name + "/" + DEFAULT_VERSION;
        }

        fn u256_to_usize(
            &self,
            val: &u256
        ) -> usize {
            let binding = val.to_u64_digits();
            let num = binding.first().unwrap();
            return usize::try_from(num.clone()).unwrap();
        }

        fn u256_to_u32(
            &self,
            val: &u256
        ) -> u32 {
            let binding = val.to_u32_digits();
            let num = binding.first().unwrap();
            return num.clone();
        }
    }

    /// Unit tests in Rust are normally defined within such a `#[cfg(test)]`
    /// module and test functions are marked with a `#[test]` attribute.
    /// The below code is technically just normal Rust code.
    #[cfg(test)]
    mod tests {
        /// Imports all the definitions from the outer scope so we can use them here.
        use super::*;
        use liquid::env::test;


        #[test]
        fn test_new() {
            let mut proxy = WeCrossProxy::new();
            assert_eq!(proxy.getVersion(), VERSION);
        }

        #[test]
        fn test_add_path() {
            let mut proxy = WeCrossProxy::new();
            proxy.addPath("/path/to/contract".to_string());
            assert_eq!(proxy.getPaths(), vec!["/path/to/contract"]);
        }

        #[test]
        fn test_get_paths() {
            let mut proxy = WeCrossProxy::new();
            proxy.addPath("/path/to/contract1".to_string());
            proxy.addPath("/path/to/contract2".to_string());
            assert_eq!(
                proxy.getPaths(),
                vec!["/path/to/contract1", "/path/to/contract2"]
            );
        }

        #[test]
        fn test_delete_path_list() {
            let mut proxy = WeCrossProxy::new();
            proxy.addPath("/path/to/contract1".to_string());
            proxy.addPath("/path/to/contract2".to_string());
            proxy.deletePathList();
            assert_eq!(proxy.getPaths(), Vec::<String>::new());
        }

        #[test]
        fn test_start_xa_transaction() {
            let mut proxy = WeCrossProxy::new();
            let xa_id = "xa-123456".to_string();
            let self_paths = vec!["/path/to/contract1".to_string(), "/path/to/contract2".to_string()];
            let other_paths = vec!["/path/to/other1".to_string(), "/path/to/other2".to_string()];

            // Test normal case
            assert_eq!(
                proxy.startXATransaction(xa_id.clone(), self_paths.clone(), other_paths.clone()),
                SUCCESS_FLAG.to_string()
            );
            assert!(proxy.isExistedXATransaction(&xa_id));

            // Test xa_transaction already exists case
            assert_eq!(
                proxy.startXATransaction(xa_id.clone(), self_paths.clone(), other_paths.clone()),
                liquid::env::revert(&("xa transaction ".to_string() + &xa_id + " already exists"))
            );

            // Test locked contract case
            proxy.lockedContracts.insert(
                Address::from_low_u64_be(1),
                LockedContract {
                    locked: true,
                    xaTransactionID: "xa-999".to_string(),
                },
            );
            assert_eq!(
                proxy.startXATransaction(xa_id.clone(), vec!["/path/to/contract1".to_string()], vec![]),
                liquid::env::revert(&("/path/to/contract1 is locked by unfinished xa transaction: xa-999".to_string()))
            );
        }

        #[test]
        fn test_commit_xa_transaction() {
            let mut proxy = WeCrossProxy::new();
    
            // Add a new path to the proxy
            proxy.addPath("/test/path".to_string());
    
            // Create a new XA transaction
            let xa_transaction_id = "xa-transaction-1".to_string();
            proxy.createXATransaction(&xa_transaction_id, "/test/path".to_string());
    
            // Commit the XA transaction
            assert_eq!(proxy.commitXATransaction(xa_transaction_id.clone()), SUCCESS_FLAG);
    
            // Verify that the XA transaction has been committed
            let xa_transaction = proxy.getXATransaction(&xa_transaction_id);
            assert_eq!(xa_transaction.status, XA_STATUS_COMMITTED.to_string());
    
            // Try to commit the same XA transaction again (should fail)
            let result = std::panic::catch_unwind(|| proxy.commitXATransaction(xa_transaction_id.clone()));
            assert!(result.is_err());
    
            // Rollback the XA transaction
            proxy.rollbackXATransaction(xa_transaction_id.clone());
    
            // Try to commit the XA transaction again after rolling back (should fail)
            let result = std::panic::catch_unwind(|| proxy.commitXATransaction(xa_transaction_id.clone()));
            assert!(result.is_err());
        }

        #[test]
        fn test_get_xa_transaction_number() {
            let mut proxy = WeCrossProxy::new();
    
            // Initially, the number of XA transactions should be 0
            assert_eq!(proxy.getXATransactionNumber(), "0");
    
            // Create and commit an XA transaction
            let xa_transaction_id = "xa-transaction-1".to_string();
            proxy.createXATransaction(&xa_transaction_id, "/test/path".to_string());
            proxy.commitXATransaction(xa_transaction_id.clone());
    
            // Now, the number of XA transactions should be 1
            assert_eq!(proxy.getXATransactionNumber(), "1");
    
            // Create and rollback an XA transaction
            let xa_transaction_id = "xa-transaction-2".to_string();
            proxy.createXATransaction(&xa_transaction_id, "/test/path".to_string());
            proxy.rollbackXATransaction(xa_transaction_id.clone());
    
            // Now, the number of XA transactions should still be 1
            assert_eq!(proxy.getXATransactionNumber(), "1");
        }
    }
}