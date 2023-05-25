#![cfg_attr(not(feature = "std"), no_std)]
#![allow(non_snake_case)]

use liquid::storage;
use liquid_lang as liquid;

#[liquid::contract]
mod hello_we_cross {
    use super::*;

    /// Defines the state variables
    #[liquid(storage)]
    struct HelloWeCross {
        ss: storage::Vec<String>,
    }

    /// Defines the methods
    #[liquid(methods)]
    impl HelloWeCross {
        pub fn new(&mut self) {
            self.ss.initialize();
        }

        pub fn get(&self) -> Vec<String> {
            let mut vec: Vec<String> = Vec::new();
            for str in self.ss.iter() {
                vec.push(str.clone());
            }

            return vec;
        }

        pub fn getAndClear(&mut self) -> Vec<String> {
            let mut vec: Vec<String> = Vec::new();
            for str in self.ss.iter() {
                vec.push(str.clone());
            }

            while !self.ss.is_empty() {
                self.ss.pop();
            }

            return vec;
        }
 
        pub fn set(&mut self, _ss: Vec<String>) -> Vec<String> {
            while !self.ss.is_empty() {
                self.ss.pop();
            }

            for str in _ss.iter() {
                self.ss.push(str.clone());
            }

            return self.get();
        }


    }

    /// Unit tests in Rust are normally defined within such a `#[cfg(test)]`
    /// module and test functions are marked with a `#[test]` attribute.
    /// The below code is technically just normal Rust code.
    #[cfg(test)]
    mod tests {
        /// Imports all the definitions from the outer scope so we can use them here.
        use super::*;

        #[test]
        fn test_all() {
            let mut contract = HelloWeCross::new();
            assert_eq!(contract.get(), Vec::<String>::new());

            assert_eq!(contract.set(vec!["1".to_string()]), vec!["1".to_string()]);
            assert_eq!(contract.set(vec!["2".to_string()]), vec!["2".to_string()]);
            assert_eq!(contract.set(vec!["1".to_string(), "2".to_string()]), vec!["1".to_string(),"2".to_string()]);
            
            assert_eq!(contract.get(), vec!["1".to_string(),"2".to_string()]);
            assert_eq!(contract.getAndClear(), vec!["1".to_string(),"2".to_string()]);
            assert_eq!(contract.get(), Vec::<String>::new());
        }
    }
}