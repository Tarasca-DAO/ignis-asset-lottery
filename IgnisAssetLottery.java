package com.jelurida.ardor.contracts;

import nxt.addons.*;
import nxt.blockchain.TransactionType;
import nxt.http.callers.GetAccountAssetsCall;
import nxt.http.callers.GetAssetsByIssuerCall;
import nxt.http.callers.TransferAssetCall;
import nxt.http.responses.TransactionResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static nxt.blockchain.ChildChain.IGNIS;

/**
 * Contract to send a number of random assets upon payment
 */
public class IgnisAssetLottery extends AbstractContract {

    @ContractParametersProvider
    public interface Params {
        @ContractSetupParameter
        default long priceIgnis() {
            return 26*IGNIS.ONE_COIN;
        }

        @ContractSetupParameter
        default int priceGiftz() {
            return 1;
        }

        @ContractSetupParameter
        default int cardsPerPack() { return 3; }

        @ContractSetupParameter
        default String collectionRs() { return "ARDOR-YDK2-LDGG-3QL8-3Z9JD"; }

        @ContractSetupParameter
        default String validCurrency() { return "8633185858724739856"; }
    }
    /**
     * Invoke when the trigger transaction is executed in a block
     * @param context the transaction context
     */
    @Override
    public JO processTransaction(TransactionContext context) {
        // Validate that the contract is the recipient
        if (context.notSameRecipient()) {
            return new JO();
        }

        Params params = context.getParams(Params.class);
        long priceIgnis = context.getParams(Params.class).priceIgnis();
        int priceGiftz = context.getParams(Params.class).priceGiftz();
        String validCurrency = context.getParams(Params.class).validCurrency();
        int cardsPerPack = context.getParams(Params.class).cardsPerPack();
        String collectionRs = context.getParams(Params.class).collectionRs();
        String accountRs = context.getAccountRs();
        int maxPacks = 9/cardsPerPack;

        // Get trigger transaction
        TransactionResponse triggerTransaction = context.getTransaction();


        int numPacks = isGiftzPayment(triggerTransaction,validCurrency,priceGiftz);
        if (numPacks == 0) {
            numPacks = isIgnisPayment(triggerTransaction,priceIgnis);
        }

        if (numPacks == 0){
            context.logInfoMessage("CONTRACT: no packs bought, stop.");
            return new JO();
        }
        context.logInfoMessage("CONTRACT: number of packs: %d", numPacks);
        if (numPacks > maxPacks){
            numPacks = maxPacks;
            context.logInfoMessage("CONTRACT: number of packs reduced to %d to fit chain limit of 9tx", numPacks);
        }

        JO accAssets = GetAccountAssetsCall.create().account(accountRs).call();
        JA accountAssets = accAssets.getArray("accountAssets");

        JO cAssets = GetAssetsByIssuerCall.create().account(collectionRs).call();
        JA schachtel = new JA(cAssets.get("assets"));
        JA collectionAssets = new JA(schachtel.getObject(0));

        long randomSeed = 0;
        context.initRandom(randomSeed);


        // Select random recipient account, your chance of being selected is proportional to the sum of your payments
        Map<String, Long> assetsForDraw = getAssetsForDraw(accountAssets, collectionAssets);
        ContractAndSetupParameters contractAndParameters = context.loadContract("DistributedRandomNumberGenerator");
        Contract<Map<String, Long>, String> distributedRandomNumberGenerator = (Contract<Map<String, Long>, String>) contractAndParameters.getContract();
        //Contract<Map<String, Long>, String> distributedRandomNumberGenerator = context.loadContract("DistributedRandomNumberGenerator");
        DelegatedContext delegatedContext = new DelegatedContext(context, distributedRandomNumberGenerator.getClass().getName(), contractAndParameters.getParams());
        //DelegatedContext delegatedContext = new DelegatedContext(context, distributedRandomNumberGenerator.getClass().getName());
        //distributedRandomNumberGenerator.processInvocation(delegatedContext, assetsForDraw);

        Map<String,Integer> pack = new HashMap<String, Integer>(numPacks*cardsPerPack);
        for (int j=0;j<numPacks;j++){
            for (int i=0;i<cardsPerPack;i++){
                String asset = distributedRandomNumberGenerator.processInvocation(delegatedContext, assetsForDraw);
                Object curValue =  pack.putIfAbsent(asset,1);
                if (curValue != null){
                    pack.put(asset, (Integer) curValue + 1);
                }
                context.logInfoMessage("CONTRACT: selected asset: %s", asset);
            }
        }

        // execute the transactions
        pack.forEach((asset,quantity) -> {
                TransferAssetCall transferAsset = TransferAssetCall.create(2)
                       .recipient(triggerTransaction.getSenderRs())
                       .asset(asset)
                       .quantityQNT(quantity);
                context.createTransaction(transferAsset);});

        //String selectedAsset = distributedRandomNumberGenerator.processInvocation(delegatedContext, assetsForDraw);
        context.logInfoMessage("CONTRACT: done");
        return context.getResponse();
    }


    // function to compile the list of assets and their weight in the random draw of cards.
    // weights are determined based on the original quantity in the collection assets
    private Map<String,Long> getAssetsForDraw(JA accountAssets, JA collectionAssets){

        // right now getAssetsForDraw really only copies the collectionassets into a map
        // TODO account for the available assets in the account (in case the contract account will be empty
        Map<String,Long> assets = collectionAssets.objects()
                .stream()
                .collect(
                        Collectors.toMap(
                                asset->asset.getString("asset"),
                                asset-> asset.getLong("quantityQNT")
                        )
                );

        return assets;
    }

    private int isGiftzPayment(TransactionResponse response, String currency, int priceGiftz) {
        TransactionType Type = response.getTransactionType();
        if (Type.getType() == 5 & Type.getSubtype() == 3) {
            JO attachment = response.getAttachmentJson();
            String txcurrency = attachment.getString("currency");
            if (txcurrency.equals(currency)) {
                int unitsQNT = attachment.getInt("unitsQNT");
                return unitsQNT / priceGiftz;
            }
        }
        return 0;  // TODO: not yet implemented
    }

    private int isIgnisPayment(TransactionResponse response, long priceIgnis) {
        long amount = response.getAmount();
        long boughtPacks = amount/priceIgnis;
        if (boughtPacks>=1){
            return (int) boughtPacks;
        }
        return 0;
    }
    // getAssets

    // getOwnBalances

    // calculateNumPacks

    // drawCards
    private void packCards(JO AccountAssets){

        return;
    }
}
