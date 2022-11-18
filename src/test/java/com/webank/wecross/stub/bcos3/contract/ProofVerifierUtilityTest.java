package com.webank.wecross.stub.bcos3.contract;

import static junit.framework.TestCase.assertTrue;

import com.webank.wecross.stub.bcos3.common.MerkleProofUtility;
import com.webank.wecross.stub.bcos3.common.ObjectMapperFactory;
import java.io.IOException;
import org.fisco.bcos.sdk.client.protocol.response.BcosBlock;
import org.fisco.bcos.sdk.client.protocol.response.TransactionReceiptWithProof;
import org.fisco.bcos.sdk.client.protocol.response.TransactionWithProof;
import org.fisco.bcos.sdk.crypto.CryptoSuite;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.junit.Before;
import org.junit.Test;

public class ProofVerifierUtilityTest {
    private String blockJson =
            "{\"dbHash\":\"0x70e6fa150a77f34c71ad9e9923734a740e0bd0a3eeb3cf9a804c43e6012b16bd\",\"extraData\":[],\"gasLimit\":\"0x0\",\"gasUsed\":\"0x0\",\"hash\":\"0xd9e9241be0853aacc88b1ff921eb598af0080a100514e192e9a449f577b3a2ef\",\"logsBloom\":\"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000\",\"number\":\"0x9\",\"parentHash\":\"0x4ee40e592d4f7243faba04d69d6c8e158c1e2663398484d83bf27827bdbb3117\",\"receiptsRoot\":\"0xe6caae405e8ae50737cd7a39c9d1cba83335594bf180af40538c970b87ae7bf8\",\"sealer\":\"0x1\",\"sealerList\":[\"2ddbc04bea0d57eeb09c2828e3eeca6e392f5d25515210eb4f338aae49233a0fdfcb5f7f6229830729a42c518118645a0e936f353e2795d966494fd494af124d\",\"8d08802246badaa9bf3eed5ba56d6c4b6811dbc0c22dd3aa17ddc566e38470a1fd078eab763c6546f7946e71279c1e466540433ac6b1463f6a60dcd85d2b7004\",\"924d8a3da3ec715a7dda5f860c53e1d1706bc6c7033e18cfa0a093ec07114fda05236a9951dc6fd88baaa1af80490627beb7db826072ee49dca9335190414428\",\"a53da8b4819cd99afac393b961078bd680eb28311d941e5f051b82ebaf6e916c51655dbf6237aa2598d7a364e8714343b1d28109de98f2c7dcdcda60621651c9\"],\"stateRoot\":\"0x70e6fa150a77f34c71ad9e9923734a740e0bd0a3eeb3cf9a804c43e6012b16bd\",\"timestamp\":\"0x17152c9a056\",\"transactions\":[\"0x8b3946912d1133f9fb0722a7b607db2456d468386c2e86b035e81ef91d94eb90\"],\"transactionsRoot\":\"0x1c816600c113c0639c88ea96d269645bb52fe5a73f64e3496864d2ad2ceec6c0\"}";
    private String transactionAndProofJson =
            "{\"transaction\":{\"blockHash\":\"0xd9e9241be0853aacc88b1ff921eb598af0080a100514e192e9a449f577b3a2ef\",\"blockNumber\":\"0x9\",\"from\":\"0x35039a08bd5aa848fe9ce1c49bf1e3c2ba640434\",\"gas\":\"0x11e1a300\",\"gasPrice\":\"0x11e1a300\",\"hash\":\"0x8b3946912d1133f9fb0722a7b607db2456d468386c2e86b035e81ef91d94eb90\",\"input\":\"0x4ed3885e000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000016100000000000000000000000000000000000000000000000000000000000000\",\"nonce\":\"0x242c9a986cbe196f6b02e65de5c6caf85d5ba9ec86dd6d46dd1a1555b48c97b\",\"to\":\"0x7ba8711a62d7e1377988efff0cb9de45c6353169\",\"transactionIndex\":\"0x0\",\"value\":\"0x0\"},\"txProof\":[{\"left\":[],\"right\":[]}]}";
    private String transactionReceiptAndProofJson =
            "{\"receiptProof\":[{\"left\":[],\"right\":[]}],\"transactionReceipt\":{\"blockHash\":\"0xd9e9241be0853aacc88b1ff921eb598af0080a100514e192e9a449f577b3a2ef\",\"blockNumber\":\"0x9\",\"contractAddress\":\"0x0000000000000000000000000000000000000000\",\"from\":\"0x35039a08bd5aa848fe9ce1c49bf1e3c2ba640434\",\"gasUsed\":\"0x802c\",\"input\":\"0x4ed3885e000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000016100000000000000000000000000000000000000000000000000000000000000\",\"logs\":[],\"logsBloom\":\"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000\",\"output\":\"0x\",\"root\":\"0x70e6fa150a77f34c71ad9e9923734a740e0bd0a3eeb3cf9a804c43e6012b16bd\",\"status\":\"0x0\",\"to\":\"0x7ba8711a62d7e1377988efff0cb9de45c6353169\",\"transactionHash\":\"0x8b3946912d1133f9fb0722a7b607db2456d468386c2e86b035e81ef91d94eb90\",\"transactionIndex\":\"0x0\"}}";

    private String getTransactionReceiptAndReceiptProofJson =
            "{\"blockHash\":\"0xd9e9241be0853aacc88b1ff921eb598af0080a100514e192e9a449f577b3a2ef\",\"blockNumber\":\"0x9\",\"contractAddress\":\"0x0000000000000000000000000000000000000000\",\"from\":\"0x35039a08bd5aa848fe9ce1c49bf1e3c2ba640434\",\"gasUsed\":\"0x802c\",\"input\":\"0x4ed3885e000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000016100000000000000000000000000000000000000000000000000000000000000\",\"logs\":[],\"logsBloom\":\"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000\",\"output\":\"0x\",\"root\":\"0x70e6fa150a77f34c71ad9e9923734a740e0bd0a3eeb3cf9a804c43e6012b16bd\",\"status\":\"0x0\",\"to\":\"0x7ba8711a62d7e1377988efff0cb9de45c6353169\",\"transactionHash\":\"0x8b3946912d1133f9fb0722a7b607db2456d468386c2e86b035e81ef91d94eb90\",\"transactionIndex\":\"0x0\",\"receiptProof\":[{\"left\":[],\"right\":[]}],\"txProof\":[{\"left\":[],\"right\":[]}]}";

    private String blockJson0 =
            "{\"dbHash\":\"0x9abce0e65719f06ee104abf1539e42beef25c164f889420eab9c055cc95de03c\",\"extraData\":[],\"gasLimit\":\"0x0\",\"gasUsed\":\"0x0\",\"hash\":\"0x83eab142175649d0bc5a93bda004d02f165cd5aaf10ec988e72d8873b8c6de95\",\"logsBloom\":\"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000\",\"number\":\"0x23\",\"parentHash\":\"0x92f0701a02f47cf53f9906a5808ab2ff368aa81dcd22c942cad4567ce0ffb3c1\",\"receiptsRoot\":\"0x57da96f64cd53b6223fa82a2e499075b20bf45947824cea35ac8345b1d8069d8\",\"sealer\":\"0x2\",\"sealerList\":[\"2ddbc04bea0d57eeb09c2828e3eeca6e392f5d25515210eb4f338aae49233a0fdfcb5f7f6229830729a42c518118645a0e936f353e2795d966494fd494af124d\",\"8d08802246badaa9bf3eed5ba56d6c4b6811dbc0c22dd3aa17ddc566e38470a1fd078eab763c6546f7946e71279c1e466540433ac6b1463f6a60dcd85d2b7004\",\"924d8a3da3ec715a7dda5f860c53e1d1706bc6c7033e18cfa0a093ec07114fda05236a9951dc6fd88baaa1af80490627beb7db826072ee49dca9335190414428\",\"a53da8b4819cd99afac393b961078bd680eb28311d941e5f051b82ebaf6e916c51655dbf6237aa2598d7a364e8714343b1d28109de98f2c7dcdcda60621651c9\"],\"stateRoot\":\"0x9abce0e65719f06ee104abf1539e42beef25c164f889420eab9c055cc95de03c\",\"timestamp\":\"0x1715335555f\",\"transactions\":[\"0xf76341248f90e743618a8152fb10f851af4ddf8ac297137d6668214f58334dc7\",\"0x4389024348c8f4adf2b0ce54e76f057ca786fbc0198a4ba10b4a19d674171152\",\"0x633a3386a189455354c058af6606d705697f3b216ad555958dc680f68cc4e99d\",\"0x483197261994b1267b2f2ff2ab48fcd8981b0ef7a9ccb9def497ae11ac29d1c0\",\"0xab700e2e0a079e19fa4442e7d4e18a59fddeb2efaa225d4df5b12a80d59c4ae3\"],\"transactionsRoot\":\"0x215ba810f73d5efd87e0e77936cdca4bf8bb37fbd5b4c00cefa73d3d762d4a41\"}";
    private String transactionAndProofJson0 =
            "{\"transaction\":{\"blockHash\":\"0x83eab142175649d0bc5a93bda004d02f165cd5aaf10ec988e72d8873b8c6de95\",\"blockNumber\":\"0x23\",\"from\":\"0x65d168d7499e72d45ed02de1c05a5197448612c6\",\"gas\":\"0x1c9c380\",\"gasPrice\":\"0x1c9c380\",\"hash\":\"0x633a3386a189455354c058af6606d705697f3b216ad555958dc680f68cc4e99d\",\"input\":\"0x3fe8e3f50000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000003b9aca00000000000000000000000000000000000000000000000000000000000000000a3565386331356434323200000000000000000000000000000000000000000000\",\"nonce\":\"0x1d9bdf6d0f5b9caa568734f2eeab6fd19dfa6545a83b8313c33db9b3f690856\",\"to\":\"0x0000000000000000000000000000000000005002\",\"transactionIndex\":\"0x2\",\"value\":\"0x0\"},\"txProof\":[{\"left\":[\"80f76341248f90e743618a8152fb10f851af4ddf8ac297137d6668214f58334dc7\",\"014389024348c8f4adf2b0ce54e76f057ca786fbc0198a4ba10b4a19d674171152\"],\"right\":[\"03483197261994b1267b2f2ff2ab48fcd8981b0ef7a9ccb9def497ae11ac29d1c0\",\"04ab700e2e0a079e19fa4442e7d4e18a59fddeb2efaa225d4df5b12a80d59c4ae3\"]},{\"left\":[],\"right\":[]}]}";

    private String transactionReceiptAndProofJson0 =
            "{\"receiptProof\":[{\"left\":[\"803fb462c75987b2001a83dbed279aeecec047b1f9bc9c56e195a74932fbaae0b1\",\"01e9fe05b4c30ed7eb927e24b788016114c1c31bf3771181c4d83bb3416c04ba09\"],\"right\":[\"03495a1e82c707f19aa55f2135b1dfddf992f39f6a614a910e001b9c00685e66d5\",\"04cf051dd6960c184742948ee3f10883c3bf11f51da6ed6c67eacd37972338ebde\"]},{\"left\":[],\"right\":[]}],\"transactionReceipt\":{\"blockHash\":\"0x83eab142175649d0bc5a93bda004d02f165cd5aaf10ec988e72d8873b8c6de95\",\"blockNumber\":\"0x23\",\"contractAddress\":\"0x0000000000000000000000000000000000000000\",\"from\":\"0x65d168d7499e72d45ed02de1c05a5197448612c6\",\"gasUsed\":\"0x10a88\",\"input\":\"0x3fe8e3f50000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000003b9aca00000000000000000000000000000000000000000000000000000000000000000a3565386331356434323200000000000000000000000000000000000000000000\",\"logs\":[],\"logsBloom\":\"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000\",\"output\":\"0x0000000000000000000000000000000000000000000000000000000000000000\",\"root\":\"0x9abce0e65719f06ee104abf1539e42beef25c164f889420eab9c055cc95de03c\",\"status\":\"0x0\",\"to\":\"0x0000000000000000000000000000000000005002\",\"transactionHash\":\"0x633a3386a189455354c058af6606d705697f3b216ad555958dc680f68cc4e99d\",\"transactionIndex\":\"0x2\"}}";

    private String getGetTransactionReceiptAndReceiptProofJson0 =
            "{\"blockHash\":\"0x83eab142175649d0bc5a93bda004d02f165cd5aaf10ec988e72d8873b8c6de95\",\"blockNumber\":\"0x23\",\"contractAddress\":\"0x0000000000000000000000000000000000000000\",\"from\":\"0x65d168d7499e72d45ed02de1c05a5197448612c6\",\"gasUsed\":\"0x10a88\",\"input\":\"0x3fe8e3f50000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000003b9aca00000000000000000000000000000000000000000000000000000000000000000a3565386331356434323200000000000000000000000000000000000000000000\",\"logs\":[],\"logsBloom\":\"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000\",\"output\":\"0x0000000000000000000000000000000000000000000000000000000000000000\",\"root\":\"0x9abce0e65719f06ee104abf1539e42beef25c164f889420eab9c055cc95de03c\",\"status\":\"0x0\",\"to\":\"0x0000000000000000000000000000000000005002\",\"transactionHash\":\"0x633a3386a189455354c058af6606d705697f3b216ad555958dc680f68cc4e99d\",\"transactionIndex\":\"0x2\",\"receiptProof\":[{\"left\":[\"803fb462c75987b2001a83dbed279aeecec047b1f9bc9c56e195a74932fbaae0b1\",\"01e9fe05b4c30ed7eb927e24b788016114c1c31bf3771181c4d83bb3416c04ba09\"],\"right\":[\"03495a1e82c707f19aa55f2135b1dfddf992f39f6a614a910e001b9c00685e66d5\",\"04cf051dd6960c184742948ee3f10883c3bf11f51da6ed6c67eacd37972338ebde\"]},{\"left\":[],\"right\":[]}],\"txProof\":[{\"left\":[\"80f76341248f90e743618a8152fb10f851af4ddf8ac297137d6668214f58334dc7\",\"014389024348c8f4adf2b0ce54e76f057ca786fbc0198a4ba10b4a19d674171152\"],\"right\":[\"03483197261994b1267b2f2ff2ab48fcd8981b0ef7a9ccb9def497ae11ac29d1c0\",\"04ab700e2e0a079e19fa4442e7d4e18a59fddeb2efaa225d4df5b12a80d59c4ae3\"]},{\"left\":[],\"right\":[]}]}";

    private BcosBlock.Block block = null;
    private TransactionReceipt receipt = null;
    private TransactionWithProof.TransactionAndProof transAndProof = null;
    private TransactionReceiptWithProof.ReceiptAndProof receiptAndProof = null;

    private BcosBlock.Block block0 = null;
    private TransactionReceipt receipt0 = null;
    private TransactionWithProof.TransactionAndProof transAndProof0 = null;
    private TransactionReceiptWithProof.ReceiptAndProof receiptAndProof0 = null;
    private CryptoSuite cryptoSuite = new CryptoSuite(0);

    @Before
    public void init() throws IOException {
        block = ObjectMapperFactory.getObjectMapper().readValue(blockJson, BcosBlock.Block.class);
        transAndProof =
                ObjectMapperFactory.getObjectMapper()
                        .readValue(
                                transactionAndProofJson,
                                TransactionWithProof.TransactionAndProof.class);
        receiptAndProof =
                ObjectMapperFactory.getObjectMapper()
                        .readValue(
                                transactionReceiptAndProofJson,
                                TransactionReceiptWithProof.ReceiptAndProof.class);
        receipt =
                ObjectMapperFactory.getObjectMapper()
                        .readValue(
                                getTransactionReceiptAndReceiptProofJson, TransactionReceipt.class);

        block0 = ObjectMapperFactory.getObjectMapper().readValue(blockJson0, BcosBlock.Block.class);
        transAndProof0 =
                ObjectMapperFactory.getObjectMapper()
                        .readValue(
                                transactionAndProofJson0,
                                TransactionWithProof.TransactionAndProof.class);
        receiptAndProof0 =
                ObjectMapperFactory.getObjectMapper()
                        .readValue(
                                transactionReceiptAndProofJson0,
                                TransactionReceiptWithProof.ReceiptAndProof.class);

        receipt0 =
                ObjectMapperFactory.getObjectMapper()
                        .readValue(
                                getGetTransactionReceiptAndReceiptProofJson0,
                                TransactionReceipt.class);
    }

    @Test
    public void verifyTransactionTest() {
        assertTrue(
                MerkleProofUtility.verifyTransaction(
                        block.getTransactionsRoot(), transAndProof, cryptoSuite));

        assertTrue(
                MerkleProofUtility.verifyTransaction(
                        block0.getTransactionsRoot(), transAndProof0, cryptoSuite));
    }

    @Test
    public void verifyTransactionReceiptTest() {

        assertTrue(
                MerkleProofUtility.verifyTransactionReceipt(
                        block.getReceiptsRoot(), receiptAndProof, null, cryptoSuite));

        assertTrue(
                MerkleProofUtility.verifyTransactionReceipt(
                        block0.getReceiptsRoot(), receiptAndProof0, null, cryptoSuite));
    }

    @Test
    public void verifyTransactionWithProofTest() {
        assertTrue(
                MerkleProofUtility.verifyTransaction(
                        receipt.getTransactionHash(),
                        receipt.getTransactionIndex(),
                        block.getTransactionsRoot(),
                        receipt.getTxProof(),
                        cryptoSuite));

        assertTrue(
                MerkleProofUtility.verifyTransaction(
                        receipt0.getTransactionHash(),
                        receipt0.getTransactionIndex(),
                        block0.getTransactionsRoot(),
                        receipt0.getTxProof(),
                        cryptoSuite));
    }

    @Test
    public void verifyTransactionReceiptWithProofTest() {
        assertTrue(
                MerkleProofUtility.verifyTransactionReceipt(
                        block.getReceiptsRoot(),
                        receipt,
                        receipt.getReceiptProof(),
                        null,
                        cryptoSuite));

        assertTrue(
                MerkleProofUtility.verifyTransactionReceipt(
                        block0.getReceiptsRoot(),
                        receipt0,
                        receipt0.getReceiptProof(),
                        null,
                        cryptoSuite));
    }
}
