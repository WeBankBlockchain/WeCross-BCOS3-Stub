package com.webank.wecross.stub.bcos3.common;

import org.fisco.bcos.sdk.v3.utils.Numeric;

import java.math.BigInteger;

public class ExtendedTransactionDecoder {
//    public static RawTransaction decode(String hexTransaction) {
//        byte[] transaction = Numeric.hexStringToByteArray(hexTransaction);
//        RlpList rlpList = RlpDecoder.decode(transaction);
//        RlpList values = (RlpList) rlpList.getValues().get(0);
//        BigInteger randomid = ((RlpString) values.getValues().get(0)).asPositiveBigInteger();
//        BigInteger gasPrice = ((RlpString) values.getValues().get(1)).asPositiveBigInteger();
//        BigInteger gasLimit = ((RlpString) values.getValues().get(2)).asPositiveBigInteger();
//        BigInteger blockLimit = ((RlpString) values.getValues().get(3)).asPositiveBigInteger();
//        String to = ((RlpString) values.getValues().get(4)).asString();
//        BigInteger value = ((RlpString) values.getValues().get(5)).asPositiveBigInteger();
//        String data = ((RlpString) values.getValues().get(6)).asString();
//
//        // add extra data
//        BigInteger chainId = ((RlpString) values.getValues().get(7)).asPositiveBigInteger();
//        BigInteger groupId = ((RlpString) values.getValues().get(8)).asPositiveBigInteger();
//        String extraData = ((RlpString) values.getValues().get(9)).asString();
//        return RawTransaction.createTransaction(
//                randomid,
//                gasPrice,
//                gasLimit,
//                blockLimit,
//                to,
//                value,
//                data,
//                chainId,
//                groupId,
//                extraData);
//    }
}
