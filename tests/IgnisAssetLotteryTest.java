package com.jelurida.ardor.contracts;

import nxt.addons.JA;
import nxt.addons.JO;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.util.stream.Collectors;

import static com.jelurida.ardor.contracts.TarascaTester.*;

public class IgnisAssetLotteryTest extends AbstractContractTest {

    // CHUCK is the collection account.

    @Test
    public void buyMultiPacksIgnis() {
        Logger.logDebugMessage("Test buyMultiPacksIgnis()");

        int collectionSize = TarascaTester.collectionSize();
        int cardsPerPack = TarascaTester.cardsPerPack();

        initCurrency(CHUCK.getSecretPhrase(), "TOLLA", "Tarascolla", "exchangeable,useful", 100000);
        initCollection(CHUCK.getSecretPhrase(), collectionSize);
        generateBlock();

        JO currencyInfo = getCollectionCurrency(CHUCK.getRsAccount());
        String currencyId = currencyInfo.getString("currency");

        JO setupParams = new JO();
        setupParams.put("priceIgnis", TarascaTester.priceIgnis());
        setupParams.put("cardsPerPack", cardsPerPack);
        setupParams.put("validCurrency", currencyId);
        setupParams.put("collectionRs", CHUCK.getRsAccount());

        String contractName = ContractTestHelper.deployContract(IgnisAssetLottery.class, setupParams, false);
        ContractTestHelper.deployContract(DistributedRandomNumberGenerator.class, null, true);
        generateBlock();

        JA collectionAssets = TarascaTester.getCollectionAssets(CHUCK.getRsAccount());
        TarascaTester.sendAssets(collectionAssets, 300, CHUCK.getSecretPhrase(), ALICE.getRsAccount(), "to Bob");

        generateBlock();
        buyPacksIgnis(2, contractName, DAVE.getSecretPhrase());
        generateBlock();
        buyPacksIgnis(1, contractName, BOB.getSecretPhrase());
        generateBlock();
        generateBlock();

        // Check bob bought a pack.
        JO bobsResponse = getAccountAssets(BOB.getRsAccount());
        JA bobsAssets = new JA(bobsResponse.get("accountAssets")); // Need to unbox another array
        int numBobs = bobsAssets.objects().stream().map(a -> a.getInt("quantityQNT")).collect(Collectors.summingInt(i -> i));

        // Check dave bought 2 packs
        JO davesResponse = getAccountAssets(DAVE.getRsAccount());
        JA davesAssets = new JA(davesResponse.get("accountAssets")); // Need to unbox another array
        int numDaves = davesAssets.objects().stream().map(a -> a.getInt("quantityQNT")).collect(Collectors.summingInt(i -> i));
        ;

        Assert.assertTrue(numBobs == 1 * cardsPerPack);
        Assert.assertTrue(numDaves == 2 * cardsPerPack);
    }

    @Test
    public void buyMultiPacksGiftz() {
        Logger.logDebugMessage("Test buyMultiPacksIgnis()");

        int collectionSize = TarascaTester.collectionSize();
        int cardsPerPack = TarascaTester.cardsPerPack();

        initCurrency(CHUCK.getSecretPhrase(), "TOLLA", "Tarascolla", "exchangeable,useful", 100000);
        initCollection(CHUCK.getSecretPhrase(), collectionSize);
        generateBlock();

        JO currencyInfo = getCollectionCurrency(CHUCK.getRsAccount());
        String currencyId = currencyInfo.getString("currency");

        JO setupParams = new JO();
        setupParams.put("priceIgnis", TarascaTester.priceIgnis());
        setupParams.put("cardsPerPack", cardsPerPack);
        setupParams.put("validCurrency", currencyId);
        setupParams.put("collectionRs", CHUCK.getRsAccount());

        String contractName = ContractTestHelper.deployContract(IgnisAssetLottery.class, setupParams, false);
        ContractTestHelper.deployContract(DistributedRandomNumberGenerator.class, null, true);
        generateBlock();


        JA collectionAssets = TarascaTester.getCollectionAssets(CHUCK.getRsAccount());
        TarascaTester.sendAssets(collectionAssets,300,CHUCK.getSecretPhrase(),ALICE.getRsAccount(),"to Bob");
        JO coin = TarascaTester.getCollectionCurrency(CHUCK.getRsAccount());
        String curId = coin.getString("currency");
        TarascaTester.sendCoin(curId,1000,BOB.getRsAccount(),CHUCK.getSecretPhrase());
        TarascaTester.sendCoin(curId,1000,DAVE.getRsAccount(),CHUCK.getSecretPhrase());

        generateBlock();
        generateBlock();

        // Now the contract runner has cards to sell. Everything ready for the test.
        JO resGiftzBob = buyPacksGiftz(1,contractName,BOB.getSecretPhrase());
        generateBlock();
        JO resGiftzDave = buyPacksGiftz(2,contractName,DAVE.getSecretPhrase());
        generateBlock();
        generateBlock();
        generateBlock();
        generateBlock();

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


}

