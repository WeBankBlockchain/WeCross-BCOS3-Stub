package com.webank.wecross.stub.bcos3.client;

import java.util.Objects;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.model.CryptoType;

public abstract class AbstractClientWrapper implements ClientWrapper {

    private Client client;
    private CryptoSuite cryptoSuite;

    public AbstractClientWrapper(Client client) {
        this.client = client;
        this.cryptoSuite =
                Objects.nonNull(client)
                        ? client.getCryptoSuite()
                        : new CryptoSuite(CryptoType.ECDSA_TYPE);
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public CryptoSuite getCryptoSuite() {
        return cryptoSuite;
    }

    public void setCryptoSuite(CryptoSuite cryptoSuite) {
        this.cryptoSuite = cryptoSuite;
    }
}
