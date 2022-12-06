package com.webank.wecross.stub.bcos3;

import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.bcos3.account.BCOSAccount;
import com.webank.wecross.stub.bcos3.common.BCOSConstant;
import org.junit.Test;

import java.util.Objects;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class BCOSStubFactoryTest {

    private final BCOS3EcdsaEvmStubFactory bcosSubFactory = new BCOS3EcdsaEvmStubFactory();
    //private final BCOS3GMEvmStubFactory bcosSubFactory = new BCOS3GMEvmStubFactory();
    //private final BCOS3EcdsaWasmStubFactory bcosSubFactory = new BCOS3EcdsaWasmStubFactory();
    //private final BCOS3GMWasmStubFactory bcosSubFactory = new BCOS3GMWasmStubFactory();

    @Test
    public void newConnectionTest() {
        Connection connection = bcosSubFactory.newConnection("./");
        assertTrue(Objects.isNull(connection));
    }

    @Test
    public void newDriverTest() {
        Driver driver = bcosSubFactory.newDriver();
        assertTrue(Objects.nonNull(driver));
        assertTrue(driver instanceof BCOSDriver);
    }

    @Test
    public void newAccountTest() {
        Account account = bcosSubFactory.newAccount("bcos", "classpath:/accounts/bcos");
        assertTrue(Objects.nonNull(account));
        assertTrue(account instanceof BCOSAccount);
        BCOSAccount bcosAccount = (BCOSAccount) account;
        assertEquals(
                bcosAccount.getCredentials().getAddress(),
                "0x4c9e341a015ce8200060a028ce45dfea8bf33e15");
        assertEquals(bcosAccount.getName(), "bcos");
        assertEquals(bcosAccount.getType(), "BCOS3_ECDSA_EVM");
    }

    @Test
    public void newAccountFailedTest() {
        Account account = bcosSubFactory.newAccount("bcos_xxx", "classpath:/accounts/bcos_xxx");
        assertTrue(Objects.isNull(account));
    }

    @Test
    public void BCOSSubFactoryTypeTest() {
        System.out.println(bcosSubFactory.getStubType());
        assertEquals(BCOSConstant.BCOS3_ECDSA_EVM_STUB_TYPE, bcosSubFactory.getStubType());
        assertEquals(bcosSubFactory.getAlg(), BCOSConstant.SECP256K1);

//        assertEquals(BCOSConstant.BCOS3_GM_EVM_STUB_TYPE, bcosSubFactory.getStubType());
//        assertEquals(bcosSubFactory.getAlg(), BCOSConstant.SM2P256V1);
//
//        assertEquals(BCOSConstant.BCOS3_ECDSA_WASM_STUB_TYPE, bcosSubFactory.getStubType());
//        assertEquals(bcosSubFactory.getAlg(), BCOSConstant.SECP256K1);
//
//        assertEquals(BCOSConstant.BCOS3_GM_WASM_STUB_TYPE, bcosSubFactory.getStubType());
//        assertEquals(bcosSubFactory.getAlg(), BCOSConstant.SM2P256V1);
    }
}
