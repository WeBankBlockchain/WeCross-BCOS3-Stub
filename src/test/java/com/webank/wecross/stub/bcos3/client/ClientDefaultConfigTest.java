package com.webank.wecross.stub.bcos3.client;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;

import org.junit.Test;

public class ClientDefaultConfigTest {
    @Test
    public void clientDefaultConfigTest() {
        assertEquals(ClientDefaultConfig.DEFAULT_GROUP_ID, "group0");
        assertEquals(ClientDefaultConfig.DEFAULT_CHAIN_ID, "chain0");
        assertEquals(ClientDefaultConfig.DEFAULT_SERVICE_TIMEOUT, 60000);
        assertEquals(ClientDefaultConfig.DEFAULT_SERVICE_THREAD_NUMBER, 16);
        assertFalse(ClientDefaultConfig.DEFAULT_SERVICE_DISABLE_SSL);
    }
}
