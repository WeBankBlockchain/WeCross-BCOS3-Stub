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
        fn mkdir(&mut self, absolutePath: String) -> i32;
        fn link(&mut self, name: String, version: String, _address: String, _abi: String) -> i32;
        fn readlink(&self, absolutePath: String) -> Address;
    }
}

mod sys {
    #[link(wasm_import_module = "bcos")]
    extern "C" {
        pub fn call(
            address_offset: u32,
            address_length: u32,
            data_offset: u32,
            data_length: u32,
        ) -> u32;

        pub fn getReturnDataSize() -> u32;

        pub fn getReturnData(result_offset: u32);
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
            return String::from(VERSION);
        }

        pub fn addPath(&mut self, _path: String) {
            self.pathCache.push(_path);
        }

        pub fn getPaths(&self) -> Vec<String> {
            let mut vec: Vec<String> = Vec::new();
            for path in self.pathCache.iter() {
                vec.push(path.clone());
            }

            return vec;
        }

        pub fn deletePathList(&mut self) {
            while !self.pathCache.is_empty() {
                self.pathCache.pop();
            }
        }

        /* don not support deploy contract
        pub fn deployContract(&mut self, _bin: Bytes) -> Address {
            return Address::empty();
        }

        pub fn deployContractWithRegisterBFS (&mut self, 
            _path: String,
            _bin: Bytes,
            _abi: String
        ) -> Address {
            return Address::empty();
        } */

        pub fn linkBFS(&mut self, 
            _path: String, 
            _addr: String,
            _abi: String
        ) {    
            let name = self.getNameByPath(&_path);
            let addr = self.getAddressByName(&name, false);

            if addr != Address::empty() {
                match self.lockedContracts.get(&addr) {
                    Some(status) => {
                        if status.locked {
                            liquid::env::revert(&(name.clone() + " is locked by unfinished xa transaction: " + &status.xaTransactionID));
                        }
                    }
                    None => (),
                }
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
                
                return (String::from(""), String::from(""), String::from(""));
            }

            let bfsInfo = bfsList.first().unwrap();
            return (bfsInfo.file_name.clone(), bfsInfo.ext[0].to_string(), bfsInfo.ext[1].to_string());
        }

        pub fn constantCallWithXa(
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

            match self.lockedContracts.get(&addr) {
                Some(status) => {
                    if status.xaTransactionID != _XATransactionID {
                        liquid::env::revert(&(_path + " is unregistered in xa transaction: " + &_XATransactionID));
                    }
                }
                None => liquid::env::revert(&(_path + " is unregistered in xa transaction: " + &_XATransactionID)),
            }

            return self.callContract_internal_with_func(&addr, &_func, &_args);
        }

        pub fn constantCall(
            &mut self,
            _name: String,
            _argsWithMethodId: Bytes
        ) -> Bytes {
            let addr = self.getAddressByName(&_name, true);

            match self.lockedContracts.get(&addr) {
                Some(status) => {
                    if status.locked {
                        liquid::env::revert(&("resource is locked by unfinished xa transaction: ".to_string() + &status.xaTransactionID));
                    }
                }
                None => (),
            }

            return self.callContract_internal_with_data(&addr, &_argsWithMethodId);
        }

        pub fn sendTransactionWithXa(
            &mut self,
            _uid: String,
            _XATransactionID: String,
            _XATransactionSeq: u256,
            _path: String,
            _func: String,
            _args: Bytes,
        ) -> Bytes {

            match self.transactions.get(&_uid) {
                Some(trans) => {
                    if trans.existed {
                        return trans.result.clone();
                    }
                }
                None => (),
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

            match self.lockedContracts.get(&addr) {
                Some(status) => {
                    if status.xaTransactionID != _XATransactionID {
                        liquid::env::revert(&(_path.clone() + " is unregistered in xa transaction " + &_XATransactionID));
                    }
                }
                None => liquid::env::revert(&(_path.clone() + " is unregistered in xa transaction " + &_XATransactionID)),
            }

            if !self.isValidXATransactionSep(&_XATransactionID, &_XATransactionSeq) {
                liquid::env::revert(&"seq should be greater than before".to_string());
            }

            let key = self.getXATransactionStepKey(&_XATransactionID, &_XATransactionSeq);
            self.xaTransactionSteps.insert(key.clone(), XATransactionStep {
                accountIdentity: self.env().get_tx_origin().to_string(),
                timestamp: (self.env().now() / 1000).into(),
                path: _path,
                contractAddress: addr.clone(),
                func: _func.clone(),
                args: _args.clone(),
            });

            // recode seq
            let num = self.xaTransactions[&_XATransactionID].stepNum.clone();
            self.xaTransactions[&_XATransactionID].seqs.push(_XATransactionSeq);
            self.xaTransactions[&_XATransactionID].stepNum = num + 1.into();

            let result = self.callContract_internal_with_func(&addr, &_func, &_args);

            self.transactions.insert(_uid, Transaction {
                existed: true, 
                result: result.clone(),
            });

            return result;
        }

        pub fn sendTransaction(
            &mut self,
            _uid: String,
            _name: String,
            _argsWithMethodId: Bytes
        ) -> Bytes {

            match self.transactions.get(&_uid) {
                Some(trans) => {
                    if trans.existed {
                        return trans.result.clone();
                    }
                }
                None => (),
            }

            let addr = self.getAddressByName(&_name, true);

            match self.lockedContracts.get(&addr) {
                Some(status) => {
                    if status.locked {
                        liquid::env::revert(&(_name + " is locked by unfinished xa transaction: " + &status.xaTransactionID));
                    }
                }
                None => (),
            }

            let result = self.callContract_internal_with_data(&addr, &_argsWithMethodId);

            self.transactions.insert(_uid, Transaction {
                existed: true, 
                result: result.clone(),
            });

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

                match self.lockedContracts.get(&addr) {
                    Some(status) => {
                        if status.locked {
                            liquid::env::revert(&(self_path.to_string() + " is locked by unfinished xa transaction: " + &status.xaTransactionID));
                        }
                    }
                    None => (),
                }

                self.lockedContracts.insert(addr.clone(), ContractStatus {
                    locked: true,
                    xaTransactionID:  _xaTransactionID.clone(),
                });
                allPaths.push(self_path.to_string());
            }

            for other_path in _otherPaths.iter() {
                allPaths.push(other_path.to_string());
            }

            // recode xa transaction
            self.xaTransactions.insert(_xaTransactionID.clone(), XATransaction{
                accountIdentity: self.env().get_tx_origin().to_string(),
                paths: allPaths,
                contractAddresses: contracts,
                status: XA_STATUS_PROCESSING.to_string(),
                startTimestamp: (self.env().now() / 1000).into(),
                commitTimestamp: 0.into(),
                rollbackTimestamp: 0.into(),
                seqs: Vec::new(),
                stepNum: 0.into(),
            });

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
                let func = self.xaTransactionSteps[&key].func.clone();
                let contractAddress = self.xaTransactionSteps[&key].contractAddress.clone();
                let args = &self.xaTransactionSteps[&key].args;

                let mut data = hash(self.getRevertFunc(&func, REVERT_FLAG).as_bytes()).to_vec();
                data.truncate(4);
                data.extend(&(args).encode());

                let status = self.sys_call(&contractAddress.as_bytes(), &data);
                if status != 0 {
                    message =  message + " revert \"" + &func + "\" failed.'";
                    result = message.clone();
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
            let index = if "-1".to_string() == _index {
                if len == 0 { 0 } else { len - 1 }
            } else { 
                match _index.parse() {
                    Ok(number) => number,
                    Err(_) => 0,
                }
            };

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
                            &self.u256_to_string(&self.xaTransactions[&xaTransactionID].startTimestamp) +
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
                        &self.u256_to_string(&self.xaTransactions[&xaTransactionID].startTimestamp) +
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
                            &self.u256_to_string(&self.xaTransactions[&_xaTransactionID].startTimestamp) +
                            r#","commitTimestamp":"# +
                            &self.u256_to_string(&self.xaTransactions[&_xaTransactionID].commitTimestamp) +
                            r#","rollbackTimestamp":"# +
                            &self.u256_to_string(&self.xaTransactions[&_xaTransactionID].rollbackTimestamp) +
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
            
            match self.lockedContracts.get(&addr) {
                Some(status) => {
                    if !status.locked {
                        return NULL_FLAG.to_string()
                    } else {
                        let xaTransactionID = status.xaTransactionID.clone();
                        match self.xaTransactions[&xaTransactionID].seqs.last() {
                            Some(seq) => {
                                return xaTransactionID + &" " + &self.u256_to_string(seq);
                            }
                            None => return xaTransactionID + &" 0",
                        }
                    }
                }
                None => return NULL_FLAG.to_string()
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
            let mut data: Vec<u8> = hash(_sig.as_bytes()).to_vec();
            data.truncate(4);
            data.extend(&(_args).encode());
            
            return self.call_with_address_data(_contractAddress, &data);
        }

        fn callContract_internal_with_data(
            &mut self,
            _contractAddress: &Address,
            _argsWithMethodId: &Bytes
        ) -> Bytes {
            return self.call_with_address_data(_contractAddress, _argsWithMethodId);
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
                            &self.u256_to_string(_XATransactionSeq) +
                            r#","accountIdentity":""# + 
                            &_xaTransactionStep.accountIdentity +
                            r#"","path":""# + 
                            &_xaTransactionStep.path + 
                            r#"","timestamp":"# + 
                            &self.u256_to_string(&_xaTransactionStep.timestamp) +
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
            return self.xaTransactions.contains_key(_xaTransactionID);
        }

        fn isValidXATransactionSep(
            &self,
            _xaTransactionID: &String,
            _XATransactionSeq: &u256
        ) -> bool {
            let index = self.xaTransactions[_xaTransactionID].stepNum.clone();
            return index == 0.into() || _XATransactionSeq > &self.xaTransactions[_xaTransactionID].seqs.last().unwrap();
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
            return _transactionID.to_string() + &self.u256_to_string(_transactionSeq);
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

            match binding.first() {
                Some(i) => usize::try_from(i.clone()).unwrap(),
                None => 0,
            }
        }

        fn u256_to_u32(
            &self,
            val: &u256
        ) -> u32 {
            let binding = val.to_u32_digits();

            match binding.first() {
                Some(i) => i.clone(),
                None => 0,
            }
        }

        fn u256_to_string(&self, num: &u256) -> String {
            let binding = num.to_u64_digits();

            match binding.first() {
                Some(i) => i.to_string(),
                None => String::from("0"),
            }
        }

        fn call_with_address_data(&mut self, address: &Address, data: &[u8]) -> Bytes {
            let status = self.sys_call(address.as_bytes(), data);
            if status != 0 {
                liquid::env::revert(&("call contract failed: ".to_string() + &address.to_string()));
            }
            let return_data_size = self.sys_get_return_data_size();
            let mut return_data_buffer = liquid_prelude::vec::from_elem(0u8, return_data_size as usize);
            self.sys_get_return_data(&mut return_data_buffer);
            return Bytes::from(return_data_buffer);
        }

        fn sys_call(&mut self, address: &[u8], data: &[u8]) -> u32 {
            unsafe {
                sys::call(
                    address.as_ptr() as u32,
                    address.len() as u32,
                    data.as_ptr() as u32,
                    data.len() as u32,
                )
            }
        }
        
        fn sys_get_return_data_size(&mut self) -> u32 {
            unsafe { sys::getReturnDataSize() }
        }
        
        fn sys_get_return_data(&mut self, result: &mut [u8]) {
            unsafe {
                sys::getReturnData(result.as_ptr() as u32);
            }
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
        use predicates::ord::eq;

        #[test]
        fn test_new() {
            let proxy = WeCrossProxy::new();
            assert_eq!(proxy.getVersion(), VERSION);
        }

        #[test]
        fn test_addPath() {
            let mut proxy = WeCrossProxy::new();
            proxy.addPath("/path/to/contract".to_string());
            assert_eq!(proxy.getPaths(), vec!["/path/to/contract"]);
        }

        #[test]
        fn test_getPaths() {
            let mut proxy = WeCrossProxy::new();
            proxy.addPath("zone.chain.contract1".to_string());
            proxy.addPath("zone.chain.contract2".to_string());
            assert_eq!(
                proxy.getPaths(),
                vec!["zone.chain.contract1", "zone.chain.contract2"]
            );
        }

        #[test]
        fn test_deletePathList() {
            let mut proxy = WeCrossProxy::new();
            proxy.addPath("zone.chain.contract1".to_string());
            proxy.addPath("zone.chain.contract2".to_string());
            proxy.deletePathList();
            assert_eq!(proxy.getPaths(), Vec::<String>::new());
        }

        #[test]
        fn test_linkBFS() {
            let mut proxy = WeCrossProxy::new();
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));
            let link_ctx = Bfs::link_context();
            link_ctx.expect().returns(0);
            
            proxy.linkBFS("zone.chain.contract0".to_string(), "address0".to_string(), "abi".to_string());
            assert_eq!(proxy.pathCache.pop().unwrap(), "zone.chain.contract0");

            readlink_ctx.expect().returns(Address::empty());
            proxy.linkBFS("zone.chain.contract0".to_string(), "address0".to_string(), "abi".to_string());
            assert_eq!(proxy.pathCache.pop().unwrap(), "zone.chain.contract0");
        }

        #[test]
        #[should_panic(expected = "contract0 is locked by unfinished xa transaction: xa-id-0")]
        fn test_linkBFS_lock_paninc() {
            let mut proxy = WeCrossProxy::new();
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));
            let link_ctx = Bfs::link_context();
            link_ctx.expect().returns(0);
            
            proxy.lockedContracts.insert(Address::from("address0"), ContractStatus {
                locked: true,
                xaTransactionID: "xa-id-0".to_string(),
            });
            proxy.linkBFS("zone.chain.contract0".to_string(), "address0".to_string(), "abi".to_string());
        }

        #[test]
        #[should_panic(expected = "contract0:latest unable link to BFS, error: 1")]
        fn test_linkBFS_link_paninc() {
            let mut proxy = WeCrossProxy::new();
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));
            let link_ctx = Bfs::link_context();
            link_ctx.expect().returns(1);
            
            proxy.linkBFS("zone.chain.contract0".to_string(), "address0".to_string(), "abi".to_string());
        }

        #[test]
        fn test_readlink() {
            let proxy = WeCrossProxy::new();
            let list_ctx = Bfs::list_context();
            list_ctx.expect().when(eq(String::from(BFS_APPS.to_string() + "empty_path1" + "/" + DEFAULT_VERSION))).returns_fn(|_|(0, Vec::new()));
            assert_eq!(proxy.readlink("empty_path1".to_string()), ("".to_string(), "".to_string(), "".to_string()));

            list_ctx.expect().when(eq(String::from(BFS_APPS.to_string() + "empty_path2" + "/" + DEFAULT_VERSION))).returns_fn(|_|(-1, Vec::new()));
            assert_eq!(proxy.readlink("empty_path2".to_string()), ("".to_string(), "".to_string(), "".to_string()));

            list_ctx.expect().when(eq(String::from(BFS_APPS.to_string() + "not_link_path" + "/" + DEFAULT_VERSION))).returns_fn(|_|(1, vec![BfsInfo {
                file_name: "not_link".to_string(),
                file_type: "not_link".to_string(),
                ext: vec!["address".to_string(), "abi".to_string()],
            }]));
            assert_eq!(proxy.readlink("not_link_path".to_string()), ("".to_string(), "".to_string(), "".to_string()));

            list_ctx.expect().when(eq(String::from(BFS_APPS.to_string() + "ext_err_path" + "/" + DEFAULT_VERSION))).returns_fn(|_|(1, vec![BfsInfo {
                file_name: "ext_err".to_string(),
                file_type: "link".to_string(),
                ext: Vec::new(),
            }]));
            assert_eq!(proxy.readlink("ext_err_path".to_string()), ("".to_string(), "".to_string(), "".to_string()));

            list_ctx.expect().when(eq(String::from(BFS_APPS.to_string() + "contract" + "/" + DEFAULT_VERSION))).returns_fn(|_|(1, vec![BfsInfo {
                file_name: "latest".to_string(),    
                file_type: "link".to_string(),
                ext: vec!["address".to_string(), "abi".to_string()],
            }]));
            assert_eq!(proxy.readlink("contract".to_string()), ("latest".to_string(), "address".to_string(), "abi".to_string()));
        }

        #[test]
        #[should_panic(expected = "not implemented")]
        fn test_constantCallWithXa() {
            let mut proxy = WeCrossProxy::new();
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));

            assert_eq!(proxy.startXATransaction(
                "xa-id-0".to_string(), 
                vec!["zone.chain.contract0".to_string()], 
                vec!["zone.chain.contract1".to_string()]), SUCCESS_FLAG);

            proxy.constantCallWithXa("xa-id-0".to_string(), "zone.chain.contract0".to_string(), "set".to_string(), Bytes::from("hello".as_bytes()));
        }

        #[test]
        #[should_panic(expected = "xa transaction not found")]
        fn test_constantCallWithXa_not_found_panic() {
            let mut proxy = WeCrossProxy::new();
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));

            proxy.constantCallWithXa("xa-id-0".to_string(), "zone.chain.contract0".to_string(), "set".to_string(), Bytes::from("hello".as_bytes()));
        }

        #[test]
        #[should_panic(expected = "zone.chain.contract1 is unregistered in xa transaction: xa-id-0")]
        fn test_constantCallWithXa_not_unregistered_panic() {
            let mut proxy = WeCrossProxy::new();
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().when(eq(String::from(BFS_APPS.to_string() + "contract0" + "/" + DEFAULT_VERSION))).returns(Address::from("address0"));
            readlink_ctx.expect().when(eq(String::from(BFS_APPS.to_string() + "contract1" + "/" + DEFAULT_VERSION))).returns(Address::from("address1"));

            assert_eq!(proxy.startXATransaction(
                "xa-id-0".to_string(), 
                vec!["zone.chain.contract0".to_string()], 
                vec!["zone.chain.contract1".to_string()]), SUCCESS_FLAG);
            proxy.constantCallWithXa("xa-id-0".to_string(), "zone.chain.contract1".to_string(), "set".to_string(), Bytes::from("hello".as_bytes()));
        }

        #[test]
        #[should_panic(expected = "not implemented")]
        fn test_constantCall() {
            let mut proxy = WeCrossProxy::new();
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));

            proxy.constantCall("zone.chain.contract1".to_string(), Bytes::from("hello".as_bytes()));
        }

        #[test]
        #[should_panic(expected = "resource is locked by unfinished xa transaction: xa-id-0")]
        fn test_constantCall_locked_painc() {
            let mut proxy = WeCrossProxy::new();
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));

            proxy.lockedContracts.insert(Address::from("address0"), ContractStatus {
                locked: true,
                xaTransactionID: "xa-id-0".to_string(),
            });
            proxy.constantCall("zone.chain.contract1".to_string(), Bytes::from("hello".as_bytes()));
        }

        #[test]
        #[should_panic(expected = "not implemented")]
        fn test_sendTransactionWithXa() {
            let mut proxy = WeCrossProxy::new();
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));

            proxy.transactions.insert("tran-uuid-existed".to_string(), Transaction {
                existed: true,
                result: Bytes::from("world".as_bytes()),
            });

            assert_eq!(proxy.sendTransactionWithXa(
                "tran-uuid-existed".to_string(), 
                "xa-id-existed".to_string(), 
                12323.into(), 
                "zone.chain.contract0".to_string(), 
                "set".to_string(),
                Bytes::from("hello".as_bytes())), Bytes::from("world".as_bytes()));

            assert_eq!(proxy.startXATransaction(
                "xa-id-0".to_string(), 
                vec!["zone.chain.contract0".to_string()], 
                vec!["zone.chain.contract1".to_string()]), SUCCESS_FLAG);
            proxy.sendTransactionWithXa(
                "tran-uuid-0".to_string(), 
                "xa-id-0".to_string(), 
                12323.into(), 
                "zone.chain.contract0".to_string(), 
                "set".to_string(),
                Bytes::from("hello".as_bytes()));
        }

        #[test]
        #[should_panic(expected = "seq should be greater than before")]
        fn test_sendTransactionWithXa_seq_panic() {
            let mut proxy = WeCrossProxy::new();
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));

            assert_eq!(proxy.startXATransaction(
                "xa-id-0".to_string(), 
                vec!["zone.chain.contract0".to_string()], 
                vec!["zone.chain.contract1".to_string()]), SUCCESS_FLAG);

            proxy.xaTransactions["xa-id-0"].seqs.push(1234.into());
            proxy.xaTransactions["xa-id-0"].stepNum = 1.into();
            proxy.sendTransactionWithXa(
                "tran-uuid-0".to_string(), 
                "xa-id-0".to_string(), 
                12.into(),
                "zone.chain.contract0".to_string(), 
                "set".to_string(),
                Bytes::from("hello".as_bytes()));
        }

        #[test]
        #[should_panic(expected = "not implemented")]
        fn test_sendTransaction() {
            let mut proxy = WeCrossProxy::new();
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));

            proxy.transactions.insert("tran-uuid-existed".to_string(), Transaction {
                existed: true,
                result: Bytes::from("world".as_bytes()),
            });

            assert_eq!(proxy.sendTransaction(
                "tran-uuid-existed".to_string(), 
                "contract".to_string(),
                Bytes::from("hello".as_bytes())), Bytes::from("world".as_bytes()));

            proxy.sendTransaction(
                "tran-uuid-0".to_string(), 
                "contract".to_string(),
                Bytes::from("hello".as_bytes()));
        }

        #[test]
        #[should_panic(expected = "contract is locked by unfinished xa transaction: xa-id-0")]
        fn test_sendTransaction_locked_painc() {
            let mut proxy = WeCrossProxy::new();
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));

            proxy.lockedContracts.insert(Address::from("address0"), ContractStatus {
                locked: true,
                xaTransactionID: "xa-id-0".to_string(),
            });

            proxy.sendTransaction(
                "tran-uuid-0".to_string(), 
                "contract".to_string(),
                Bytes::from("hello".as_bytes()));
        }

        #[test]
        fn test_startXATransaction() {
            let mut proxy = WeCrossProxy::new();
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));

            assert_eq!(proxy.startXATransaction(
                "xa-id-0".to_string(), 
                vec!["zone.chain.contract0".to_string()], 
                vec!["zone.chain.contract1".to_string()]), SUCCESS_FLAG);
        }

        #[test]
        #[should_panic(expected = "xa transaction xa-id-0 already exists")]
        fn test_startXATransaction_exists_painc() {
            let mut proxy = WeCrossProxy::new();
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));

            proxy.startXATransaction(
                "xa-id-0".to_string(), 
                vec!["zone.chain.contract0".to_string()], 
                vec!["zone.chain.contract1".to_string()]);
            proxy.startXATransaction(
                "xa-id-0".to_string(), 
                vec!["zone.chain.contract0".to_string()], 
                vec!["zone.chain.contract1".to_string()]); 
        }

        #[test]
        #[should_panic(expected = "zone.chain.contract0 is locked by unfinished xa transaction: xa-id-lock")]
        fn test_startXATransaction_lock_painc() {
            let mut proxy = WeCrossProxy::new();
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));

            proxy.lockedContracts.insert(Address::from("address0"), ContractStatus {
                locked: true,
                xaTransactionID: "xa-id-lock".to_string(),
            });
            proxy.startXATransaction(
                "xa-id-0".to_string(), 
                vec!["zone.chain.contract0".to_string()], 
                vec!["zone.chain.contract1".to_string()]);
        }

        #[test]
        fn test_commitXATransaction() {
            let mut proxy = WeCrossProxy::new();
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));

            assert_eq!(proxy.startXATransaction(
                "xa-id-0".to_string(), 
                vec!["zone.chain.contract0".to_string()], 
                vec!["zone.chain.contract1".to_string()]), SUCCESS_FLAG);
            assert_eq!(proxy.commitXATransaction("xa-id-0".to_string()), SUCCESS_FLAG);
        }

        #[test]
        #[should_panic(expected = "xa transaction not found")]
        fn test_commitXATransaction_panic() {
            let mut proxy = WeCrossProxy::new();
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));

            assert_eq!(proxy.startXATransaction(
                "xa-id-0".to_string(), 
                vec!["zone.chain.contract0".to_string()], 
                vec!["zone.chain.contract1".to_string()]), SUCCESS_FLAG);
            proxy.commitXATransaction("xa-id-1".to_string());
        }

        #[test]
        #[should_panic(expected = "not implemented")]
        fn test_rollbackXATransaction() {
            let mut proxy = WeCrossProxy::new();
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice.clone());
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));

            assert_eq!(proxy.startXATransaction(
                "xa-id-0".to_string(), 
                vec!["zone.chain.contract0".to_string()], 
                vec!["zone.chain.contract1".to_string()]), SUCCESS_FLAG);
            assert_eq!(proxy.rollbackXATransaction("xa-id-0".to_string()), SUCCESS_FLAG);

            assert_eq!(proxy.startXATransaction(
                "xa-id-1".to_string(), 
                vec!["zone.chain.contract0".to_string()], 
                vec!["zone.chain.contract1".to_string()]), SUCCESS_FLAG);
            proxy.xaTransactions["xa-id-1"].seqs.push(1.into());
            proxy.xaTransactions["xa-id-1"].stepNum = 1.into();
            proxy.xaTransactionSteps.insert("xa-id-1".to_string() + "1", XATransactionStep {
                accountIdentity: accounts.alice.to_string(),
                timestamp: 1234567.into(),
                path: "zone.chain.contract2".to_string(),
                contractAddress: "address2".into(),
                func: "get(string,string)".to_string(),
                args: Bytes::from("hello".as_bytes()),
            });

            assert_eq!(proxy.rollbackXATransaction("xa-id-1".to_string()), SUCCESS_FLAG);
        }

        #[test]
        fn test_getXATransactionNumber() {
            let mut proxy = WeCrossProxy::new();
            assert_eq!(proxy.getXATransactionNumber(), "0");

            proxy.xaTransactionIDs.push("xa-id-0".to_string());
            assert_eq!(proxy.getXATransactionNumber(), "1");
        }

        #[test]
        fn test_listXATransactions() {
            let mut proxy = WeCrossProxy::new();
            assert_eq!(proxy.listXATransactions("0".to_string(), 10.into()), "{\"tota\":0,\"xaTransactions\":[]}");
            assert_eq!(proxy.listXATransactions("-1".to_string(), 10.into()), "{\"tota\":0,\"xaTransactions\":[]}");
            assert_eq!(proxy.listXATransactions("-2".to_string(), 10.into()), "{\"tota\":0,\"xaTransactions\":[]}");

            let accounts: test::DefaultAccounts = test::default_accounts();

            proxy.xaTransactionIDs.push("xa-id-0".to_string());
            proxy.xaTransactions.insert("xa-id-0".to_string(), XATransaction{
                accountIdentity: accounts.alice.to_string(),
                paths: vec!["zone.chain.contract0".to_string(), "zone.chain.contract1".to_string()],
                contractAddresses: vec!["address0".into(), "address1".into()],
                status: XA_STATUS_PROCESSING.to_string(),
                startTimestamp: 1234567.into(),
                commitTimestamp: 0.into(),
                rollbackTimestamp: 0.into(),
                seqs: Vec::new(),
                stepNum: 0.into(),
            });

            assert_eq!(proxy.listXATransactions("0".to_string(), 10.into()), "{\"total\":1,\"xaTransactions\":[{\"xaTransactionID\":\"xa-id-0\",\"accountIdentity\":\"alice\",\"status\":\"processing\",\"paths\":[\"zone.chain.contract0\",\"zone.chain.contract1\"],\"timestamp\":1234567}]}");
            assert_eq!(proxy.listXATransactions("-1".to_string(), 10.into()), "{\"total\":1,\"xaTransactions\":[{\"xaTransactionID\":\"xa-id-0\",\"accountIdentity\":\"alice\",\"status\":\"processing\",\"paths\":[\"zone.chain.contract0\",\"zone.chain.contract1\"],\"timestamp\":1234567}]}");
            assert_eq!(proxy.listXATransactions("1".to_string(), 10.into()), "{\"tota\":0,\"xaTransactions\":[]}");
            assert_eq!(proxy.listXATransactions("2".to_string(), 10.into()), "{\"tota\":0,\"xaTransactions\":[]}");

            proxy.xaTransactionIDs.push("xa-id-1".to_string());
            proxy.xaTransactions.insert("xa-id-1".to_string(), XATransaction{
                accountIdentity: accounts.alice.to_string(),
                paths: vec!["zone.chain.contract02".to_string(), "zone.chain.contract3".to_string()],
                contractAddresses: vec!["address2".into(), "address3".into()],
                status: XA_STATUS_PROCESSING.to_string(),
                startTimestamp: 1234567.into(),
                commitTimestamp: 0.into(),
                rollbackTimestamp: 0.into(),
                seqs: Vec::new(),
                stepNum: 0.into(),
            });

            assert_eq!(proxy.listXATransactions("1".to_string(), 10.into()), "{\"total\":2,\"xaTransactions\":[{\"xaTransactionID\":\"xa-id-1\",\"accountIdentity\":\"alice\",\"status\":\"processing\",\"paths\":[\"zone.chain.contract02\",\"zone.chain.contract3\"],\"timestamp\":1234567},{\"xaTransactionID\":\"xa-id-0\",\"accountIdentity\":\"alice\",\"status\":\"processing\",\"paths\":[\"zone.chain.contract0\",\"zone.chain.contract1\"],\"timestamp\":1234567}]}");
            assert_eq!(proxy.listXATransactions("-1".to_string(), 10.into()), "{\"total\":2,\"xaTransactions\":[{\"xaTransactionID\":\"xa-id-1\",\"accountIdentity\":\"alice\",\"status\":\"processing\",\"paths\":[\"zone.chain.contract02\",\"zone.chain.contract3\"],\"timestamp\":1234567},{\"xaTransactionID\":\"xa-id-0\",\"accountIdentity\":\"alice\",\"status\":\"processing\",\"paths\":[\"zone.chain.contract0\",\"zone.chain.contract1\"],\"timestamp\":1234567}]}");
            assert_eq!(proxy.listXATransactions("1".to_string(), 1.into()), "{\"total\":2,\"xaTransactions\":[{\"xaTransactionID\":\"xa-id-1\",\"accountIdentity\":\"alice\",\"status\":\"processing\",\"paths\":[\"zone.chain.contract02\",\"zone.chain.contract3\"],\"timestamp\":1234567}]}");
            assert_eq!(proxy.listXATransactions("0".to_string(), 10.into()), "{\"total\":2,\"xaTransactions\":[{\"xaTransactionID\":\"xa-id-0\",\"accountIdentity\":\"alice\",\"status\":\"processing\",\"paths\":[\"zone.chain.contract0\",\"zone.chain.contract1\"],\"timestamp\":1234567}]}");
            assert_eq!(proxy.listXATransactions("2".to_string(), 10.into()), "{\"tota\":0,\"xaTransactions\":[]}");
        }

        #[test]
        #[should_panic(expected = "xa transaction not found")]
        fn test_getXATransaction_panic() {
            let proxy = WeCrossProxy::new();
            proxy.getXATransaction("xa-id-no-found".to_string());
        }

        #[test]
        fn test_getXATransaction() {
            let mut proxy = WeCrossProxy::new();
            let accounts: test::DefaultAccounts = test::default_accounts();

            // Test empty step
            proxy.xaTransactionIDs.push("xa-id-0".to_string());
            proxy.xaTransactions.insert("xa-id-0".to_string(), XATransaction{
                accountIdentity: accounts.alice.to_string(),
                paths: vec!["zone.chain.contract0".to_string(), "zone.chain.contract1".to_string()],
                contractAddresses: vec!["address0".into(), "address1".into()],
                status: XA_STATUS_PROCESSING.to_string(),
                startTimestamp: 1234567.into(),
                commitTimestamp: 0.into(),
                rollbackTimestamp: 0.into(),
                seqs: Vec::new(),
                stepNum: 0.into(),
            });
            assert_eq!(proxy.getXATransaction("xa-id-0".to_string()), "{\"xaTransactionID\":\"xa-id-0\",\"accountIdentity\":\"alice\",\"status\":\"processing\",\"paths\":[\"zone.chain.contract0\",\"zone.chain.contract1\"],\"startTimestamp\":1234567,\"commitTimestamp\":0,\"rollbackTimestamp\":0,\"xaTransactionSteps\":[]}");

            // test 1 step
            proxy.xaTransactionIDs.push("xa-id-1".to_string());
            proxy.xaTransactions.insert("xa-id-1".to_string(), XATransaction{
                accountIdentity: accounts.alice.to_string(),
                paths: vec!["zone.chain.contract2".to_string(), "zone.chain.contract3".to_string()],
                contractAddresses: vec!["address2".into(), "address3".into()],
                status: XA_STATUS_PROCESSING.to_string(),
                startTimestamp: 1234567.into(),
                commitTimestamp: 0.into(),
                rollbackTimestamp: 0.into(),
                seqs: vec![1.into()],
                stepNum: 1.into(),
            });

            proxy.xaTransactionSteps.insert("xa-id-1".to_string() + "1", XATransactionStep {
                accountIdentity: accounts.alice.to_string(),
                timestamp: 1234567.into(),
                path: "zone.chain.contract2".to_string(),
                contractAddress: "address2".into(),
                func: "get(string,string)".to_string(),
                args: Bytes::from("hello".as_bytes()),
            });
            assert_eq!(proxy.getXATransaction("xa-id-1".to_string()), "{\"xaTransactionID\":\"xa-id-1\",\"accountIdentity\":\"alice\",\"status\":\"processing\",\"paths\":[\"zone.chain.contract2\",\"zone.chain.contract3\"],\"startTimestamp\":1234567,\"commitTimestamp\":0,\"rollbackTimestamp\":0,\"xaTransactionSteps\":[{\"xaTransactionSeq\":1,\"accountIdentity\":\"alice\",\"path\":\"zone.chain.contract2\",\"timestamp\":1234567,\"method\":\"get\",\"args\":\"68656c6c6f\"}]}");
        
            // test 2 step
            proxy.xaTransactionIDs.push("xa-id-2".to_string());
            proxy.xaTransactions.insert("xa-id-2".to_string(), XATransaction{
                accountIdentity: accounts.alice.to_string(),
                paths: vec!["zone.chain.contract4".to_string(), "zone.chain.contract5".to_string()],
                contractAddresses: vec!["address4".into(), "address5".into()],
                status: XA_STATUS_PROCESSING.to_string(),
                startTimestamp: 1234567.into(),
                commitTimestamp: 0.into(),
                rollbackTimestamp: 0.into(),
                seqs: vec![1.into(), 123.into()],
                stepNum: 2.into(),
            });

            proxy.xaTransactionSteps.insert("xa-id-2".to_string() + "1", XATransactionStep {
                accountIdentity: accounts.alice.to_string(),
                timestamp: 1234567.into(),
                path: "zone.chain.contract2".to_string(),
                contractAddress: "address2".into(),
                func: "get(string,string)".to_string(),
                args: Bytes::from("hello".as_bytes()),
            });

            proxy.xaTransactionSteps.insert("xa-id-2".to_string() + "123", XATransactionStep {
                accountIdentity: accounts.alice.to_string(),
                timestamp: 1234567.into(),
                path: "zone.chain.contract2".to_string(),
                contractAddress: "address2".into(),
                func: "set_time(string)".to_string(),
                args: Bytes::from("1231234324".as_bytes()),
            });

            assert_eq!(proxy.getXATransaction("xa-id-2".to_string()), "{\"xaTransactionID\":\"xa-id-2\",\"accountIdentity\":\"alice\",\"status\":\"processing\",\"paths\":[\"zone.chain.contract4\",\"zone.chain.contract5\"],\"startTimestamp\":1234567,\"commitTimestamp\":0,\"rollbackTimestamp\":0,\"xaTransactionSteps\":[{\"xaTransactionSeq\":1,\"accountIdentity\":\"alice\",\"path\":\"zone.chain.contract2\",\"timestamp\":1234567,\"method\":\"get\",\"args\":\"68656c6c6f\"},{\"xaTransactionSeq\":123,\"accountIdentity\":\"alice\",\"path\":\"zone.chain.contract2\",\"timestamp\":1234567,\"method\":\"set_time\",\"args\":\"31323331323334333234\"}]}");
        }
    
        #[test]
        fn test_getLatestXATransaction() {
            let mut proxy = WeCrossProxy::new();
            let accounts: test::DefaultAccounts = test::default_accounts();
            proxy.head.set(0.into());
            proxy.tail.set(0.into());
            assert_eq!(proxy.getLatestXATransaction(), "{}");

            proxy.head.set(0.into());
            proxy.tail.set(1.into());
            proxy.xaTransactionIDs.push("xa-id-0".to_string());
            proxy.xaTransactions.insert("xa-id-0".to_string(), XATransaction{
                accountIdentity: accounts.alice.to_string(),
                paths: vec!["zone.chain.contract0".to_string(), "zone.chain.contract1".to_string()],
                contractAddresses: vec!["address0".into(), "address1".into()],
                status: XA_STATUS_PROCESSING.to_string(),
                startTimestamp: 1234567.into(),
                commitTimestamp: 0.into(),
                rollbackTimestamp: 0.into(),
                seqs: Vec::new(),
                stepNum: 0.into(),
            });
            assert_eq!(proxy.getLatestXATransaction(), "{\"xaTransactionID\":\"xa-id-0\",\"accountIdentity\":\"alice\",\"status\":\"processing\",\"paths\":[\"zone.chain.contract0\",\"zone.chain.contract1\"],\"startTimestamp\":1234567,\"commitTimestamp\":0,\"rollbackTimestamp\":0,\"xaTransactionSteps\":[]}");
        }
    
        #[test]
        fn test_getLatestXATransactionID() {
            let mut proxy = WeCrossProxy::new();
            proxy.head.set(0.into());
            proxy.tail.set(0.into());
            assert_eq!(proxy.getLatestXATransactionID(), NULL_FLAG);

            proxy.head.set(0.into());
            proxy.tail.set(1.into());
            proxy.xaTransactionIDs.push("xa-id-0".to_string());
            assert_eq!(proxy.getLatestXATransactionID(), "xa-id-0");
        }

        #[test]
        #[should_panic(expected = "not implemented")]
        fn test_rollbackAndDeleteXATransactionTask() {
            let mut proxy = WeCrossProxy::new();
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice.clone());
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx.expect().returns(Address::from("address0"));

            assert_eq!(proxy.startXATransaction(
                "xa-id-0".to_string(), 
                vec!["zone.chain.contract0".to_string()], 
                vec!["zone.chain.contract1".to_string()]), SUCCESS_FLAG);
            assert_eq!(proxy.rollbackAndDeleteXATransactionTask("xa-id-0".to_string()), SUCCESS_FLAG);

            assert_eq!(proxy.startXATransaction(
                "xa-id-1".to_string(), 
                vec!["zone.chain.contract0".to_string()], 
                vec!["zone.chain.contract1".to_string()]), SUCCESS_FLAG);
            proxy.xaTransactions["xa-id-1"].seqs.push(1.into());
            proxy.xaTransactions["xa-id-1"].stepNum = 1.into();
            proxy.xaTransactionSteps.insert("xa-id-1".to_string() + "1", XATransactionStep {
                accountIdentity: accounts.alice.to_string(),
                timestamp: 1234567.into(),
                path: "zone.chain.contract2".to_string(),
                contractAddress: "address2".into(),
                func: "get(string,string)".to_string(),
                args: Bytes::from("hello".as_bytes()),
            });

            assert_eq!(proxy.rollbackAndDeleteXATransactionTask("xa-id-1".to_string()), SUCCESS_FLAG);
        }

        #[test]
        fn test_getXATransactionState() {
            let mut proxy = WeCrossProxy::new();
            let accounts: test::DefaultAccounts = test::default_accounts();
            let readlink_ctx = Bfs::readlink_context();
            readlink_ctx
                .expect()
                .returns(Address::from("address0"));
            assert_eq!(proxy.getXATransactionState("zone.chain.contract0".to_string()), NULL_FLAG);

            proxy.lockedContracts.insert(Address::from("address0"), ContractStatus {
                locked: false,
                xaTransactionID: "xa-id-0".to_string(),
            });
            assert_eq!(proxy.getXATransactionState("zone.chain.contract0".to_string()), NULL_FLAG);

            proxy.lockedContracts.insert(Address::from("address0"), ContractStatus {
                locked: true,
                xaTransactionID: "xa-id-0".to_string(),
            });
            proxy.xaTransactionIDs.push("xa-id-0".to_string());
            proxy.xaTransactions.insert("xa-id-0".to_string(), XATransaction{
                accountIdentity: accounts.alice.to_string(),
                paths: vec!["zone.chain.contract0".to_string(), "zone.chain.contract1".to_string()],
                contractAddresses: vec!["address0".into(), "address1".into()],
                status: XA_STATUS_PROCESSING.to_string(),
                startTimestamp: 1234567.into(),
                commitTimestamp: 0.into(),
                rollbackTimestamp: 0.into(),
                seqs: Vec::new(),
                stepNum: 0.into(),
            });
            assert_eq!(proxy.getXATransactionState("zone.chain.contract0".to_string()), "xa-id-0 0");

            proxy.xaTransactions.insert("xa-id-0".to_string(), XATransaction{
                accountIdentity: accounts.alice.to_string(),
                paths: vec!["zone.chain.contract0".to_string(), "zone.chain.contract1".to_string()],
                contractAddresses: vec!["address0".into(), "address1".into()],
                status: XA_STATUS_PROCESSING.to_string(),
                startTimestamp: 1234567.into(),
                commitTimestamp: 0.into(),
                rollbackTimestamp: 0.into(),
                seqs: vec![1.into()],
                stepNum: 1.into(),
            });
            assert_eq!(proxy.getXATransactionState("zone.chain.contract0".to_string()), "xa-id-0 1");

            proxy.xaTransactions.insert("xa-id-0".to_string(), XATransaction{
                accountIdentity: accounts.alice.to_string(),
                paths: vec!["zone.chain.contract0".to_string(), "zone.chain.contract1".to_string()],
                contractAddresses: vec!["address0".into(), "address1".into()],
                status: XA_STATUS_PROCESSING.to_string(),
                startTimestamp: 1234567.into(),
                commitTimestamp: 0.into(),
                rollbackTimestamp: 0.into(),
                seqs: vec![1.into(), 123.into()],
                stepNum: 2.into(),
            });
            assert_eq!(proxy.getXATransactionState("zone.chain.contract0".to_string()), "xa-id-0 123");
        }
    }
}