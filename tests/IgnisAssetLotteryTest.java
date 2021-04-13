package com.jelurida.ardor.contracts;

import nxt.account.PaymentTransactionType;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.blockchain.ChildTransaction;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static com.jelurida.ardor.contracts.TarascaTester.*;

public class IgnisAssetLotteryTest extends AbstractContractTest {


    @Test
    public void buyMultiPacksIgnis() {
        Logger.logInfoMessage("TEST: buyMultiPacksIgnis(): Start test");

        int collectionSize = TarascaTester.collectionSize();
        int cardsPerPack = TarascaTester.cardsPerPack();

        initCollectionCurrency();
        initCollection(collectionSize);
        generateBlock();

        JO currencyInfo = getCollectionCurrency();
        String currencyId = currencyInfo.getString("currency");

        JO setupParams = new JO();
        setupParams.put("tdao", RIKER.getRsAccount());
        setupParams.put("tcard", RIKER.getRsAccount());
        setupParams.put("col", ALICE.getRsAccount()); // collection equals contract runner
        setupParams.put("valCur", currencyId);

        String contractName = ContractTestHelper.deployContract(IgnisAssetLottery.class, setupParams, false);
        ContractTestHelper.deployContract(DistributedRandomNumberGenerator.class, null, true);
        generateBlock();

        Logger.logInfoMessage("TEST: buyMultiPacksIgnis(): Contracts deployed");

        //SJA collectionAssets = TarascaTester.getCollectionAssets();


        Logger.logInfoMessage("TEST: buyMultiPacksIgnis(): Start playing");
        generateBlock();
        buyPacksIgnis(2, contractName, DAVE.getSecretPhrase());
        generateBlock();
        buyPacksIgnis(1, contractName, BOB.getSecretPhrase());
        generateBlock();
        generateBlock();

        Logger.logInfoMessage("TEST: buyMultiPacksIgnis(): Evaluation of results");
        // Check bob bought a pack.
        JO bobsResponse = getAccountAssets(BOB.getRsAccount());
        JA bobsAssets = new JA(bobsResponse.get("accountAssets")); // Need to unbox another array
        int numBobs = bobsAssets.objects().stream().map(a -> a.getInt("quantityQNT")).collect(Collectors.summingInt(i -> i));

        // Check dave bought 2 packs
        JO davesResponse = getAccountAssets(DAVE.getRsAccount());
        JA davesAssets = new JA(davesResponse.get("accountAssets")); // Need to unbox another array
        int numDaves = davesAssets.objects().stream().map(a -> a.getInt("quantityQNT")).collect(Collectors.summingInt(i -> i));


        Logger.logInfoMessage("TEST buyMultiPacksIgnis() results: numBobs: %d, numDaves: %d",numBobs,numDaves);
        Assert.assertTrue(numBobs == 1 * cardsPerPack);
        Assert.assertTrue(numDaves == 2 * cardsPerPack);
    }

    @Test
    public void buyMultiPacksGiftz() {
        Logger.logDebugMessage("TEST: buyMultiPacksGiftz(): Start");

        int collectionSize = TarascaTester.collectionSize();
        int cardsPerPack = TarascaTester.cardsPerPack();

        initCollectionCurrency();
        initCollection(collectionSize);
        generateBlock();

        JO currencyInfo = getCollectionCurrency();
        String currencyId = currencyInfo.getString("currency");

        JO setupParams = new JO();
        setupParams.put("tdao", RIKER.getRsAccount());
        setupParams.put("tcard", RIKER.getRsAccount());
        setupParams.put("col", ALICE.getRsAccount()); // collection equals contract runner
        setupParams.put("valCur", currencyId);

        //setupParams.put("tarascaRs", CHUCK.getRsAccount());

        String contractName = ContractTestHelper.deployContract(IgnisAssetLottery.class, setupParams, false);
        ContractTestHelper.deployContract(DistributedRandomNumberGenerator.class, null, true);
        generateBlock();

        Logger.logDebugMessage("TEST: buyMultiPacksGiftz(): Contracts deployed");
        //JA collectionAssets = TarascaTester.getCollectionAssets();
        //TarascaTester.sendAssets(collectionAssets,300,CHUCK.getSecretPhrase(),ALICE.getRsAccount(),"to Bob");
        JO coin = TarascaTester.getCollectionCurrency();
        String curId = coin.getString("currency");
        TarascaTester.sendCoin(curId,1000,BOB.getRsAccount(),ALICE.getSecretPhrase());
        TarascaTester.sendCoin(curId,1000,DAVE.getRsAccount(),ALICE.getSecretPhrase());

        generateBlock();
        generateBlock();

        Logger.logDebugMessage("TEST: buyMultiPacksGiftz(): Start playing");
        // Now the contract runner has cards to sell. Everything ready for the test.
        JO resGiftzBob = buyPacksGiftz(1,contractName,BOB.getSecretPhrase());
        generateBlock();
        JO resGiftzDave = buyPacksGiftz(2,contractName,DAVE.getSecretPhrase());
        generateBlock();
        generateBlock();


        Logger.logDebugMessage("TEST: buyMultiPacksGiftz(): Evaluate results");
        JO bobsResponse  = getAccountAssets(BOB.getRsAccount());
        JO davesResponse = getAccountAssets(DAVE.getRsAccount());


        // Check bob bought a pack.
        JA bobsAssets  = new JA(bobsResponse.get("accountAssets"));
        long numBobs  = bobsAssets.objects().stream().map(a->a.getLong("quantityQNT")).collect(Collectors.summingLong(i->i));

        // Check dave bought 3 packs
        JA davesAssets  = new JA(davesResponse.get("accountAssets"));
        long numDaves = davesAssets.objects().stream().map(a->a.getLong("quantityQNT")).collect(Collectors.summingLong(i->i));;

        Assert.assertTrue(numBobs == (long) 1*cardsPerPack);
        Assert.assertTrue(numDaves== (long) 2*cardsPerPack);
    }

    @Test
    public void maxTransactionLimits() {
        Logger.logInfoMessage("TEST: maxTransactionLimits(): Start test");

        int collectionSize = TarascaTester.collectionSize();
        int cardsPerPack = TarascaTester.cardsPerPack();

        initCollectionCurrency();
        initCollection(collectionSize);
        generateBlock();

        JO currencyInfo = getCollectionCurrency();
        String currencyId = currencyInfo.getString("currency");

        JO setupParams = new JO();
        setupParams.put("tdao", RIKER.getRsAccount());
        setupParams.put("tcard", RIKER.getRsAccount());
        setupParams.put("col", ALICE.getRsAccount()); // collection equals contract runner
        setupParams.put("valCur", currencyId);

        String contractName = ContractTestHelper.deployContract(IgnisAssetLottery.class, setupParams, false);
        ContractTestHelper.deployContract(DistributedRandomNumberGenerator.class, null, true);
        generateBlock();

        Logger.logInfoMessage("TEST: maxTransactionLimits(): Contracts deployed");

        //SJA collectionAssets = TarascaTester.getCollectionAssets();


        Logger.logInfoMessage("TEST: maxTransactionLimits(): Start playing");
        generateBlock();
        buyPacksIgnis(5, contractName, DAVE.getSecretPhrase());
        generateBlock();
        buyPacksIgnis(1, contractName, BOB.getSecretPhrase());
        generateBlock();
        generateBlock();

        Logger.logInfoMessage("TEST: maxTransactionLimits(): Evaluation of results");
        // Check bob bought a pack.
        JO bobsResponse = getAccountAssets(BOB.getRsAccount());
        JA bobsAssets = new JA(bobsResponse.get("accountAssets")); // Need to unbox another array
        int numBobs = bobsAssets.objects().stream().map(a -> a.getInt("quantityQNT")).collect(Collectors.summingInt(i -> i));

        // Check dave bought 5 packs
        JO davesResponse = getAccountAssets(DAVE.getRsAccount());
        JA davesAssets = new JA(davesResponse.get("accountAssets")); // Need to unbox another array
        int numDaves = davesAssets.objects().stream().map(a -> a.getInt("quantityQNT")).collect(Collectors.summingInt(i -> i));


        Logger.logInfoMessage("TEST maxTransactionLimits() results: received assets: numBobs: %d, numDaves: %d",numBobs,numDaves);
        Assert.assertTrue(numBobs == 1 * cardsPerPack);
        Assert.assertTrue(numDaves == 5 * cardsPerPack);
    }

    @Test
    public void confirmSplitPayment() {
        Logger.logDebugMessage("TEST: confirmSplitPayment(): Start");

        int collectionSize = TarascaTester.collectionSize();
        int cardsPerPack = TarascaTester.cardsPerPack();

        initCollectionCurrency();
        initCollection(collectionSize);
        generateBlock();

        JO currencyInfo = getCollectionCurrency();
        String currencyId = currencyInfo.getString("currency");

        JO setupParams = new JO();
        setupParams.put("tdao", BOB.getRsAccount());
        setupParams.put("tcard", CHUCK.getRsAccount());
        setupParams.put("col", ALICE.getRsAccount()); // collection equals contract runner
        setupParams.put("valCur", currencyId);


        String contractName = ContractTestHelper.deployContract(IgnisAssetLottery.class, setupParams, false);
        ContractTestHelper.deployContract(DistributedRandomNumberGenerator.class, null, true);
        generateBlock();

        Logger.logDebugMessage("TEST: confirmSplitPayment(): Contracts deployed, start playing");
        JO resGiftzDave = buyPacksIgnis(1,contractName,DAVE.getSecretPhrase());
        generateBlock();

        Logger.logDebugMessage("TEST: confirmSplitPayment(): Evaluate results");
        List<? extends ChildTransaction> transactions = getLastBlockChildTransactions(2);

        Assert.assertEquals(5,transactions.size());
        List<? extends ChildTransaction> payments =  transactions.stream()
                .filter( t -> (t.getType()== PaymentTransactionType.ORDINARY))
                .collect(Collectors.toList());
        Assert.assertEquals(2,payments.size());

        List<? extends ChildTransaction> tarascapayment =  payments.stream().filter(t -> (t.getRecipientId() == BOB.getId())).collect(Collectors.toList());
        Assert.assertEquals(1,tarascapayment.size());
        List<? extends ChildTransaction> cardpayment =  payments.stream().filter(t -> (t.getRecipientId() == CHUCK.getId())).collect(Collectors.toList());
        Assert.assertEquals(1,cardpayment.size());

        Logger.logDebugMessage("TEST: confirmSplitPayment(): Done");
    }
}

