#![cfg_attr(not(feature = "std"), no_std)]
#![allow(non_snake_case)]

use liquid::storage;
use liquid_lang as liquid;
use liquid_prelude::{
    string::{ToString},
};

#[liquid::contract]
mod we_cross_hub {
    use super::*;

    const NULL_FLAG: &str = "null";
    const VERSION: &str = "v1.0.0";
    const CALL_TYPE_QUERY: &str = "0";
    const CALL_TYPE_INVOKE: &str = "1";

    /// Defines the state variables
    #[liquid(storage)]
    struct WeCrossHub {
        increment: storage::Value<u256>,
        current_index: storage::Value<u256>,
        requests: storage::Mapping<u256, String>,
        callback_results: storage::Mapping<String, Vec<String>>,
    }

    /// Defines the methods
    #[liquid(methods)]
    impl WeCrossHub {
        pub fn new(&mut self) {
            self.increment.initialize(0.into());
            self.current_index.initialize(0.into());
            self.requests.initialize();
            self.callback_results.initialize();
        }

        pub fn getVersion(&self)  -> String {
            return VERSION.to_string();
        }

        pub fn getIncrement(&self)  -> u256 {
            return self.increment.clone();
        }

        pub fn interchainInvoke(&mut self, 
            _path: String, 
            _method: String, 
            _args: Vec<String>,
            _callbackPath: String,
            _callbackMethod: String) -> String {
                
            return self.handle_request(CALL_TYPE_INVOKE.to_string(), _path, _method, _args, _callbackPath, _callbackMethod);
        }

        pub fn interchainQuery(&mut self, 
            _path: String, 
            _method: String, 
            _args: Vec<String>,
            _callbackPath: String,
            _callbackMethod: String) -> String {

            return self.handle_request(CALL_TYPE_QUERY.to_string(), _path, _method, _args, _callbackPath, _callbackMethod);
        }

        fn handle_request(&mut self,
            _call_type: String,
            _path: String, 
            _method: String, 
            _args: Vec<String>,
            _callback_path: String,
            _callback_method: String) -> String {

            self.increment += 1.into();
            let uid = self.u256_to_string(self.increment.get());

            let mut request: Vec<String> = Vec::new();
            request.push(uid.clone());
            request.push(_call_type);
            request.push(_path);
            request.push(_method);
            request.push(self.serialize_string_array(&_args));
            request.push(_callback_path);
            request.push(_callback_method);
            request.push(self.env().get_tx_origin().to_string());
            
            self.requests.insert(self.increment.get().clone(), self.serialize_string_array(&request));

            return uid;
        }

        pub fn getInterchainRequests(&self, _num: u256) -> String {

            if self.current_index == self.increment {
                return NULL_FLAG.to_string();
            }
            
            let num = if _num < (self.increment.clone() - self.current_index.clone()) {_num} else {self.increment.clone() - self.current_index.clone()};
            let mut temp_requests: Vec<String> = Vec::new();
            
            let mut i: u256 = 0.into();
            while i < num {
                temp_requests.push(self.requests.get(&(self.current_index.clone() + i.clone() + 1.into())).unwrap().clone());
                i += 1.into();
            }

            return self.serialize_string_array(&temp_requests);
        }

        pub fn updateCurrentRequestIndex(&mut self, _index: u256) {
            
            if self.current_index < _index {
                self.current_index.set(_index);
            }
        }

        pub fn registerCallbackResult(&mut self, 
            _uid: String, 
            _tid: String,
            _seq: String,
            _errorCode: String,
            _errorMsg: String, 
            _result: Vec<String>) {

            let mut result: Vec<String> = Vec::new();
            result.push(_tid);
            result.push(_seq);
            result.push(_errorCode);
            result.push(_errorMsg);
            result.push(self.serialize_string_array(&_result));

            self.callback_results.insert(_uid, result);
        }

        pub fn selectCallbackResult(&self, _uid: String) -> Vec<String> {
            return self.callback_results[&_uid].clone();
        }

        fn serialize_string_array(&self, vec: &Vec<String>) -> String {
            if vec.len() == 0 {
                return String::from("[]");
            } 

            let mut json_str = String::from("[");
            let mut i: usize = 0;

            while i < (vec.len() - 1) {
                json_str = json_str + "\"";
                json_str = json_str + &self.json_escape(vec.get(i).unwrap().clone());
                json_str = json_str + "\",";
                i += 1;
            }

            json_str = json_str + "\"";
            json_str = json_str + &self.json_escape(vec.get(vec.len() - 1).unwrap().clone());
            json_str = json_str + "\"]";

            return json_str
        }
        
        fn json_escape(&self, value: String) -> String {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        fn u256_to_string(&self, num: &u256) -> String {
            let binding = num.to_u64_digits();

            match binding.first() {
                Some(i) => i.to_string(),
                None => String::from("0"),
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

        #[test]
        fn getVersion_works() {
            let contract = WeCrossHub::new();
            assert_eq!(contract.getVersion(), VERSION);
        }

        #[test]
        fn getIncrement_works() {
            let contract = WeCrossHub::new();
            assert_eq!(contract.getIncrement(), 0.into());
        }

        #[test]
        fn interchainInvoke_works() {
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);

            let mut contract = WeCrossHub::new();
            contract.interchainInvoke(
                "path".to_string(),
                "method".to_string(),
                vec!["a".to_string(), "b".to_string()],
                "callback_path".to_string(),
                "callback_method".to_string()
            );

            assert_eq!(contract.getIncrement(), 1.into());
            assert_eq!(contract.getInterchainRequests(1.into()), "[\"[\\\"1\\\",\\\"1\\\",\\\"path\\\",\\\"method\\\",\\\"[\\\\\\\"a\\\\\\\",\\\\\\\"b\\\\\\\"]\\\",\\\"callback_path\\\",\\\"callback_method\\\",\\\"alice\\\"]\"]");
        }

        #[test]
        fn interchainQuery_works() {
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);

            let mut contract = WeCrossHub::new();
            contract.interchainQuery(
                "path".to_string(),
                "method".to_string(),
                vec!["a".to_string(), "b".to_string()],
                "callback_path".to_string(),
                "callback_method".to_string()
            );

            assert_eq!(contract.getIncrement(), 1.into());
            assert_eq!(contract.getInterchainRequests(10.into()), "[\"[\\\"1\\\",\\\"0\\\",\\\"path\\\",\\\"method\\\",\\\"[\\\\\\\"a\\\\\\\",\\\\\\\"b\\\\\\\"]\\\",\\\"callback_path\\\",\\\"callback_method\\\",\\\"alice\\\"]\"]");
        }

        #[test]
        fn getInterchainRequests_works() {
            let accounts = test::default_accounts();
            test::set_caller(accounts.alice);

            let mut contract = WeCrossHub::new();
            contract.interchainInvoke(
                "path".to_string(),
                "method".to_string(),
                vec!["a".to_string(), "b".to_string()],
                "callback_path".to_string(),
                "callback_method".to_string()
            );

            assert_eq!(contract.getIncrement(), 1.into());
            assert_eq!(contract.getInterchainRequests(0.into()), "[]");
            assert_eq!(contract.getInterchainRequests(1.into()), "[\"[\\\"1\\\",\\\"1\\\",\\\"path\\\",\\\"method\\\",\\\"[\\\\\\\"a\\\\\\\",\\\\\\\"b\\\\\\\"]\\\",\\\"callback_path\\\",\\\"callback_method\\\",\\\"alice\\\"]\"]");
            assert_eq!(contract.getInterchainRequests(100000000.into()), "[\"[\\\"1\\\",\\\"1\\\",\\\"path\\\",\\\"method\\\",\\\"[\\\\\\\"a\\\\\\\",\\\\\\\"b\\\\\\\"]\\\",\\\"callback_path\\\",\\\"callback_method\\\",\\\"alice\\\"]\"]");
        }

        #[test]
        fn updateCurrentRequestIndex_works() {
            let mut contract = WeCrossHub::new();
            contract.updateCurrentRequestIndex(1.into());
            assert_eq!(contract.current_index,  <i32 as Into<u256>>::into(1));

            contract.updateCurrentRequestIndex(0.into());
            assert_eq!(contract.current_index, <i32 as Into<u256>>::into(1));

            contract.updateCurrentRequestIndex(100000000.into());
            assert_eq!(contract.current_index, <i32 as Into<u256>>::into(100000000));
        }

        #[test]
        fn callback_works() {
            let mut contract = WeCrossHub::new();
            contract.registerCallbackResult(
                "_uid".to_string(),
                "_tid".to_string(),
                "_seq".to_string(),
                "_errorCode".to_string(),
                "_errorMsg".to_string(),
                vec!["a".to_string(), "b".to_string()],
            );

            assert_eq!(contract.selectCallbackResult("_uid".to_string()), vec!["_tid", "_seq", "_errorCode", "_errorMsg", "[\"a\",\"b\"]"]);
        }
        
    }
}