package com.webank.wecross.stub.bcos3.contract;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.fisco.bcos.sdk.v3.codec.abi.FunctionEncoder;
import org.fisco.bcos.sdk.v3.codec.abi.FunctionReturnDecoder;
import org.fisco.bcos.sdk.v3.codec.datatypes.Function;
import org.fisco.bcos.sdk.v3.codec.datatypes.Type;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.junit.Test;

public class FunctionUtilityTest {

    private static final String[] params = new String[] {"aa", "bb", "cc"};
    private static final String[] emptyParams = new String[0];
    private static final String[] nonParams = null;
    FunctionEncoder functionEncoder = new FunctionEncoder(new CryptoSuite(CryptoType.ECDSA_TYPE));
    private static String funcName = "funcName";
    private static String funcSignature = "funcName(string[])";
    private static String funcNoneParamsSignature = "funcName()";
    private static String funcMethodId;
    private static String funcEmptyParamsMethodId;
    private static String funcNoneParamsMethodId;
    private static Function function = FunctionUtility.newDefaultFunction(funcName, params);
    private static Function emptyParamsFunction =
            FunctionUtility.newDefaultFunction(funcName, emptyParams);
    private static Function noneParamsFunction =
            FunctionUtility.newDefaultFunction(funcName, nonParams);

    @Test
    public void newFunctionTest() throws IOException {
        funcMethodId = functionEncoder.buildMethodId(funcSignature).toString();
        funcEmptyParamsMethodId = functionEncoder.buildMethodId(funcSignature).toString();
        funcNoneParamsMethodId = functionEncoder.buildMethodId(funcNoneParamsSignature).toString();
        String abi = functionEncoder.encode(function).toString();
        assertTrue(abi.startsWith(funcMethodId));
        assertTrue(funcName.equals(function.getName()));
        assertTrue(abi.startsWith(funcMethodId));
        assertTrue(function.getInputParameters().size() == 1);
        assertTrue(function.getOutputParameters().size() == 1);
    }

    @Test
    public void newFunctionWithEmptyParamsTest() throws IOException {
        String abi = functionEncoder.encode(emptyParamsFunction).toString();
        assertTrue(funcName.equals(emptyParamsFunction.getName()));
        assertTrue(abi.startsWith(funcEmptyParamsMethodId));
        assertTrue(emptyParamsFunction.getInputParameters().size() == 1);
        assertTrue(emptyParamsFunction.getOutputParameters().size() == 1);
    }

    @Test
    public void newFunctionWithNonParamsTest() throws IOException {
        String abi = functionEncoder.encode(noneParamsFunction).toString();
        assertTrue(abi.length() == FunctionUtility.MethodIDWithHexPrefixLength);
        assertTrue(funcName.equals(noneParamsFunction.getName()));
        assertTrue(abi.startsWith(funcNoneParamsMethodId));
        assertTrue(noneParamsFunction.getInputParameters().size() == 0);
        assertTrue(noneParamsFunction.getOutputParameters().size() == 1);
    }

    @Test
    public void convertToStringListTest() throws IOException {
        String abi = functionEncoder.encode(function).toString();
        assertTrue(abi.startsWith(funcMethodId));

        FunctionReturnDecoder functionReturnDecoder = new FunctionReturnDecoder();
        List<Type> typeList =
                functionReturnDecoder.decode(
                        abi.substring(FunctionUtility.MethodIDWithHexPrefixLength),
                        function.getOutputParameters());
        List<String> resultList = FunctionUtility.convertToStringList(typeList);
        assertTrue(resultList.size() == params.length);
        for (int i = 0; i < params.length; i++) {
            assertEquals(params[i], resultList.get(i));
        }
    }

    @Test
    public void emptyParamsConvertToStringListTest() throws IOException {
        Function function = FunctionUtility.newDefaultFunction(funcName, emptyParams);
        String abi = functionEncoder.encode(function).toString();
        assertTrue(abi.startsWith(funcEmptyParamsMethodId));

        assertTrue(funcName.equals(function.getName()));

        FunctionReturnDecoder functionReturnDecoder = new FunctionReturnDecoder();
        List<Type> typeList =
                functionReturnDecoder.decode(
                        abi.substring(FunctionUtility.MethodIDWithHexPrefixLength),
                        function.getOutputParameters());
        List<String> resultList = FunctionUtility.convertToStringList(typeList);
        assertTrue(resultList.isEmpty());
    }

    @Test
    public void noneParamsConvertToStringListTest() throws IOException {
        Function function = FunctionUtility.newDefaultFunction(funcName, nonParams);
        String abi = functionEncoder.encode(function).toString();
        assertTrue(abi.startsWith(funcNoneParamsMethodId));
        assertTrue(funcName.equals(function.getName()));

        FunctionReturnDecoder functionReturnDecoder = new FunctionReturnDecoder();
        List<Type> typeList =
                functionReturnDecoder.decode(
                        abi.substring(FunctionUtility.MethodIDWithHexPrefixLength),
                        function.getOutputParameters());
        List<String> resultList = FunctionUtility.convertToStringList(typeList);
        assertTrue(resultList.isEmpty());
    }

    @Test
    public void decodeOutputTest() throws IOException {
        assertTrue(Objects.isNull(FunctionUtility.decodeDefaultOutput("0x")));
        assertTrue(Objects.isNull(FunctionUtility.decodeDefaultOutput("")));

        String abi1 = functionEncoder.encode(emptyParamsFunction).toString();

        String[] output1 =
                FunctionUtility.decodeDefaultOutput(
                        "0x" + abi1.substring(FunctionUtility.MethodIDWithHexPrefixLength));
        assertTrue(output1.length == 0);

        String abi2 = functionEncoder.encode(function).toString();
        String[] output2 =
                FunctionUtility.decodeDefaultOutput(
                        abi2.substring(FunctionUtility.MethodIDWithHexPrefixLength));
        assertTrue(output2.length == params.length);
        for (int i = 0; i < output2.length; ++i) {
            assertEquals(output2[i], params[i]);
        }

        String abi3 = functionEncoder.encode(noneParamsFunction).toString();
        String[] output3 =
                FunctionUtility.decodeDefaultOutput(
                        abi3.substring(FunctionUtility.MethodIDWithHexPrefixLength));
        assertTrue(Objects.isNull(output3));
    }

    @Test
    public void decodeInputTest() throws IOException {
        assertTrue(Objects.isNull(FunctionUtility.decodeDefaultInput("0x")));

        assertTrue(Objects.isNull(FunctionUtility.decodeDefaultInput("")));

        String abi1 = functionEncoder.encode(emptyParamsFunction).toString();
        String[] input1 = FunctionUtility.decodeDefaultInput(abi1);
        assertTrue(input1.length == 0);

        String abi2 = functionEncoder.encode(function).toString();
        String[] input2 = FunctionUtility.decodeDefaultInput(abi2);
        assertTrue(input2.length == params.length);

        for (int i = 0; i < input2.length; ++i) {
            assertEquals(input2[i], params[i]);
        }

        String abi3 = functionEncoder.encode(noneParamsFunction).toString();
        String[] input3 = FunctionUtility.decodeDefaultInput(abi1);
        assertTrue(input3.length == 0);
    }

    @Test
    public void decodeTransactionReceiptInputTest() throws IOException {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus(1);
        String abi = functionEncoder.encode(function).toString();
        receipt.setInput(abi);
        receipt.setOutput("0x" + abi.substring(10));
        String[] inputs = FunctionUtility.decodeDefaultInput(receipt);
        assertTrue(inputs.length == params.length);
        String[] outputs = FunctionUtility.decodeDefaultOutput(receipt);
        assertTrue(Objects.isNull(outputs));
    }

    @Test
    public void decodeTransactionReceiptInputTest0() throws IOException {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus(0);
        String abi = functionEncoder.encode(emptyParamsFunction).toString();
        receipt.setInput(abi);
        receipt.setOutput("0x" + abi.substring(10));
        String[] inputs = FunctionUtility.decodeDefaultInput(receipt);
        assertTrue(inputs.length == emptyParams.length);
        String[] outputs = FunctionUtility.decodeDefaultOutput(receipt);
        assertTrue(outputs.length == 0);
    }

    @Test
    public void decodeTransactionReceiptInputTest1() throws IOException {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus(0);
        String abi = functionEncoder.encode(noneParamsFunction).toString();
        receipt.setInput(abi);
        receipt.setOutput("0x" + abi.substring(10));
        String[] inputs = FunctionUtility.decodeDefaultInput(receipt);
        assertTrue(Objects.isNull(inputs));
    }

    @Test
    public void decodeTransactionReceiptInputTest2() throws IOException {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus(22);

        String funcName = "funcName";
        String[] params = new String[] {"aa", "bb", "cc"};

        Function function = FunctionUtility.newDefaultFunction(funcName, params);
        String abi = functionEncoder.encode(function).toString();
        receipt.setInput(abi);
        receipt.setOutput("0x" + abi.substring(10));
        String[] inputs = FunctionUtility.decodeDefaultInput(receipt);
        assertTrue(inputs.length == params.length);
        String[] outputs = FunctionUtility.decodeDefaultOutput(receipt);
        assertTrue(Objects.isNull(outputs));
    }
}
