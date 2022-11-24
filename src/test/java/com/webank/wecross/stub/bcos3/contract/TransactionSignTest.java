package com.webank.wecross.stub.bcos3.contract;

import static junit.framework.TestCase.assertEquals;

import com.webank.wecross.stub.bcos3.common.ExtendedTransactionDecoder;
import java.io.IOException;
import java.math.BigInteger;

import org.fisco.bcos.sdk.v3.codec.abi.FunctionEncoder;
import org.fisco.bcos.sdk.v3.codec.datatypes.Function;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.junit.Test;

public class TransactionSignTest {

    @Test
    public void transactionSignTest() throws IOException {
//        CryptoSuite cryptoSuite = new CryptoSuite(CryptoType.ECDSA_TYPE);
//        BigInteger blockNumber = BigInteger.valueOf(1111111);
//
//        String funcName = "testFuncName";
//        String[] params = new String[] {"aaa", "bbbb", "ccc"};
//        FunctionEncoder functionEncoder = new FunctionEncoder(cryptoSuite);
//        Function function = FunctionUtility.newDefaultFunction(funcName, params);
//        String abiData = functionEncoder.encode(function).toString();
//
//        String to = "0xb3c223fc0bf6646959f254ac4e4a7e355b50a355";
//        String extraData = "extraData";
//
//        BigInteger groupID = BigInteger.valueOf(111);
//        BigInteger chainID = BigInteger.valueOf(222);
//
//        RawTransaction rawTransaction =
//                SignTransaction.buildTransaction(to, groupID, chainID, blockNumber, abiData);
//        CryptoKeyPair credentials = cryptoSuite.getCryptoKeyPair();
//        TransactionEncoderService transactionEncoderService =
//                new TransactionEncoderService(cryptoSuite);
//        String signTx = transactionEncoderService.encodeAndSign(rawTransaction, credentials);
//
//        RawTransaction decodeExtendedRawTransaction = ExtendedTransactionDecoder.decode(signTx);
//
//        assertEquals(SignTransaction.gasPrice, decodeExtendedRawTransaction.getGasPrice());
//        assertEquals(SignTransaction.gasLimit, decodeExtendedRawTransaction.getGasLimit());
//        assertEquals(to, decodeExtendedRawTransaction.getTo());
//        assertEquals(BigInteger.ZERO, decodeExtendedRawTransaction.getValue());
//        assertEquals(abiData, "0x" + decodeExtendedRawTransaction.getData());
//        assertEquals(groupID, decodeExtendedRawTransaction.getGroupId());
//        assertEquals(chainID, decodeExtendedRawTransaction.getFiscoChainId());
    }
}
