#![cfg_attr(not(feature = "std"), no_std)]

use liquid::storage;
use liquid_lang as liquid;

#[liquid::contract]
mod hello_world {
    use super::*;

    #[liquid(storage)]
    struct HelloWorld {
        name: storage::Value<String>,
    }

    #[liquid(methods)]
    impl HelloWorld {
        pub fn new(&mut self) {
            self.name.initialize(String::from("HelloWorld!"));
        }

        pub fn get(&self) -> String {
            self.name.clone()
        }

        pub fn set(&mut self, name: String) {
            self.name.set(name)
        }

        pub fn get1(&self, s: String) -> String {
            return s;
        }

        pub fn get2(&self, s1: String, s2: String) -> String {
            return s1 + &s2;
        }

    }

    #[cfg(test)]
    mod tests {
        use super::*;

        #[test]
        fn test_all() {
            let mut contract = HelloWorld::new();
            assert_eq!(contract.get(), "HelloWorld!");
            contract.set(String::from("Hello WeCross"));
            assert_eq!(contract.get(), "Hello WeCross");
            assert_eq!(contract.get1(String::from("Hello BCOS 3")), "Hello BCOS 3");
            assert_eq!(contract.get2(String::from("Hello FISCO"), String::from(" BCOS")), "Hello FISCO BCOS");
            assert_eq!(contract.get(), "Hello WeCross");
        }
    }
}
