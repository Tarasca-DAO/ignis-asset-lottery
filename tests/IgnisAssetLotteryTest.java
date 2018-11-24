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

}

