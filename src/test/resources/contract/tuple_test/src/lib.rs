#![cfg_attr(not(feature = "std"), no_std)]
#![allow(non_snake_case)]

use liquid::{storage, InOut};
use liquid_lang as liquid;
use liquid_prelude::{
    string::{ToString},
    vec,
};

#[liquid::contract]
mod tuple_test {
    use super::*;

    #[derive(InOut, Clone)]
    #[cfg_attr(feature = "std", derive(Debug, PartialEq, Eq))]
    pub struct Item {
        a: i32,
        b: i32,
        c: i32,
    }

    #[derive(InOut, Clone)]
    #[cfg_attr(feature = "std", derive(Debug, PartialEq, Eq))]
    pub struct Info {
        name: String,
        count: i32,
        items: Vec<Item>,
    }

    /// Defines the state variables
    #[liquid(storage)]
    struct TupleTest {
        a: storage::Value<i32>,
        b: storage::Value<Item>,
        c: storage::Value<String>,
    }

    /// Defines the methods
    #[liquid(methods)]
    impl TupleTest {
        pub fn new(&mut self, _a: i32, _b: Item, _c: String) {
            self.a.initialize(_a);
            self.b.initialize(_b);
            self.c.initialize(_c);
        }

        pub fn set1(&mut self, _a: i32, _b: Item, _c: String) {
            self.a.set(_a);
            self.b.set(_b);
            self.c.set(_c);
        }

        pub fn get1(&self) -> (i32, Item, String) {
            return (self.a.clone(), self.b.clone(), self.c.clone());
        }

        pub fn getAndSet1(&mut self, _a: i32, _b: Item, _c: String) -> (i32, Item, String) {
            self.set1(_a, _b, _c);
            return self.get1();
        }

        pub fn getAndSet2(&mut self, a: i32, b: Item, c: String) -> (i32, Item, String) {
            return (a, b, c);
        }

        pub fn getSampleTupleValue(&self, ) -> (i32, Vec<Vec<Info>>, String) {
            let a: i32 = 100;
            let mut b: Vec<Vec<Info>>  = Vec::new();
            
            let info0 = Info {
                name: "Hello world! + 1 ".to_string(),
                count: 100,
                items: vec![Item {
                    a: 1,
                    b: 2,
                    c: 3,
                }],
            };
            let info1 = Info {
                name: "Hello world! + 2 ".to_string(),
                count: 101,
                items: vec![Item {
                    a: 4,
                    b: 5,
                    c: 6,
                }],
            };
            b.push(vec![info0]);
            b.push(vec![info1]);

            let c:String = "Hello world! + 3 ".to_string();
            return (a, b, c);
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
            let item1 = Item {
                a:1,
                b:2,
                c:3,
            };
            let mut contract = TupleTest::new(1, item1.clone(), "test1".to_string());
            assert_eq!(contract.get1(), (1, item1.clone(), "test1".to_string()));

            let item2 = Item {
                a:10,
                b:20,
                c:30,
            };
            contract.set1(2, item2.clone(), "test2".to_string());
            assert_eq!(contract.get1(), (2, item2.clone(), "test2".to_string()));

            let item3 = Item {
                a:100,
                b:200,
                c:300,
            };
            assert_eq!(contract.getAndSet1(3, item3.clone(), "test3".to_string()), (3, item3.clone(), "test3".to_string()));
        
            assert_eq!(contract.getAndSet2(2, item2.clone(), "test2".to_string()), (2, item2.clone(), "test2".to_string()));
            assert_eq!(contract.get1(), (3, item3.clone(), "test3".to_string()));
            
            assert_eq!(contract.getSampleTupleValue(), (100, vec![vec![
                Info {
                    name: "Hello world! + 1 ".to_string(),
                    count: 100,
                    items: vec![Item {
                        a: 1,
                        b: 2,
                        c: 3,
                    }],
                }
            ], vec![
                Info {
                    name: "Hello world! + 2 ".to_string(),
                    count: 101,
                    items: vec![Item {
                        a: 4,
                        b: 5,
                        c: 6,
                    }],
                }
            ]], "Hello world! + 3 ".to_string()));
        }
    }
}