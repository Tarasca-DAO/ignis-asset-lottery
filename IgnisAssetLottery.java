package com.jelurida.ardor.contracts;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import nxt.util.Convert;
import nxt.addons.AbstractContract;
import nxt.addons.Contract;
import nxt.addons.ContractAndSetupParameters;
import nxt.addons.ContractParametersProvider;
import nxt.addons.ContractSetupParameter;
import nxt.addons.DelegatedContext;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.addons.TransactionContext;
import nxt.addons.ValidateChain;
import nxt.blockchain.ChildChain;
import nxt.blockchain.TransactionType;
import nxt.http.callers.GetAccountAssetsCall;
import nxt.http.callers.GetAssetsByIssuerCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.TransferAssetCall;
import nxt.http.responses.TransactionResponse;


public class IgnisAssetLottery extends AbstractContract {
    public IgnisAssetLottery() {
    }
		
	@ValidateChain(accept = {2})	
    public JO processTransaction(TransactionContext context) {
        if (context.notSameRecipient()) {
            return new JO();
        } else {
            IgnisAssetLottery.Params params = (IgnisAssetLottery.Params)context.getParams(IgnisAssetLottery.Params.class);
            long priceIgnis = ((IgnisAssetLottery.Params)context.getParams(IgnisAssetLottery.Params.class)).priceIgnis();
            long tarascaCutPerPack = ((IgnisAssetLottery.Params)context.getParams(IgnisAssetLottery.Params.class)).tarascaCutPerPack();
            String tarascaRs = ((IgnisAssetLottery.Params)context.getParams(IgnisAssetLottery.Params.class)).tarascaRs();
            int priceGiftz = ((IgnisAssetLottery.Params)context.getParams(IgnisAssetLottery.Params.class)).priceGiftz();
            String validCurrency = ((IgnisAssetLottery.Params)context.getParams(IgnisAssetLottery.Params.class)).validCurrency();
            int cardsPerPack = ((IgnisAssetLottery.Params)context.getParams(IgnisAssetLottery.Params.class)).cardsPerPack();
            String collectionRs = ((IgnisAssetLottery.Params)context.getParams(IgnisAssetLottery.Params.class)).collectionRs();
            String accountRs = context.getAccountRs();
            int maxPacks = 9 / cardsPerPack;
            int chainId = 2;
            boolean payCut = false;
            TransactionResponse triggerTransaction = context.getTransaction();
            int numPacks = this.isGiftzPayment(triggerTransaction, validCurrency, priceGiftz);
            if (numPacks == 0) {
                numPacks = this.isIgnisPayment(triggerTransaction, priceIgnis);
                payCut = true;
            }

            if (numPacks == 0) {
                context.logInfoMessage("CONTRACT: no packs bought, stop.");
                return new JO();
            } else {
                context.logInfoMessage("CONTRACT: number of packs: %d", numPacks);
                if (numPacks > maxPacks) {
                    numPacks = maxPacks;
                    context.logInfoMessage("CONTRACT: number of packs reduced to %d to fit chain limit of 9tx", maxPacks);
                }

                if (payCut) {
                    long tarascaCut = tarascaCutPerPack * (long)numPacks;
                    context.logInfoMessage("CONTRACT: paying Tarasca %d Ignis, to %s, on chain %d", tarascaCut / ChildChain.IGNIS.ONE_COIN, tarascaRs,chainId);
                    SendMoneyCall sendMoneyCall = SendMoneyCall.create(chainId).recipient(tarascaRs).amountNQT(tarascaCut);
                    context.createTransaction(sendMoneyCall);
                } else {
                    context.logInfoMessage("CONTRACT: not paying Tarasca any Ignis");
                }

                JO accAssets = GetAccountAssetsCall.create().account(accountRs).call();
                JA accountAssets = accAssets.getArray("accountAssets");
                JO cAssets = GetAssetsByIssuerCall.create().account(new String[]{collectionRs}).call();
                JA schachtel = new JA(cAssets.get("assets"));
                JA collectionAssets = new JA(schachtel.getObject(0));

                context.initRandom(context.getTransaction().getTransactionId());
                Map<String, Long> assetsForDraw = this.getAssetsForDraw(accountAssets, collectionAssets);
                ContractAndSetupParameters contractAndParameters = context.loadContract("DistributedRandomNumberGenerator");
                Contract<Map<String, Long>, String> distributedRandomNumberGenerator = contractAndParameters.getContract();
                DelegatedContext delegatedContext = new DelegatedContext(context, distributedRandomNumberGenerator.getClass().getName(), contractAndParameters.getParams());


                Map<String, Integer> pack = new HashMap(numPacks * cardsPerPack);

                for(int j = 0; j < numPacks; ++j) {
                    for(int i = 0; i < cardsPerPack; ++i) {
                        String asset = (String)distributedRandomNumberGenerator.processInvocation(delegatedContext, assetsForDraw);
                        Object curValue = pack.putIfAbsent(asset, 1);
                        if (curValue != null) {
                            pack.put(asset, (Integer)curValue + 1);
                        }

                        context.logInfoMessage("CONTRACT: selected asset: %s", asset);
                    }
                }

                pack.forEach((assetx, quantity) -> {
                    TransferAssetCall transferAsset = ((TransferAssetCall)TransferAssetCall.create(chainId).recipient(triggerTransaction.getSenderRs())).asset(assetx).quantityQNT((long)quantity);
                    context.createTransaction(transferAsset);
                });
                context.logInfoMessage("CONTRACT: done");
                return context.getResponse();
            }
        }
    }

    private Map<String, Long> getAssetsForDraw(JA accountAssets, JA collectionAssets) {
        Map<String, Long> assets = (Map)collectionAssets.objects().stream().collect(Collectors.toMap((asset) -> {
            return asset.getString("asset");
        }, (asset) -> {
            return asset.getLong("quantityQNT");
        }));
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

        return 0;
    }

    private int isIgnisPayment(TransactionResponse response, long priceIgnis) {
        long amount = response.getAmount();
        long boughtPacks = amount / priceIgnis;
        return boughtPacks >= 1L ? (int)boughtPacks : 0;
    }

    private void packCards(JO AccountAssets) {
    }

    private long hashToLong(byte[] hash) {
        BigInteger bigInteger = new BigInteger(1, new byte[] {hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0]});
        return bigInteger.longValue();
    }

    @ContractParametersProvider
    public interface Params {
        @ContractSetupParameter
        default long priceIgnis() {
            return 99L * ChildChain.IGNIS.ONE_COIN;
        }

        @ContractSetupParameter
        default long tarascaCutPerPack() {
            return 50L * ChildChain.IGNIS.ONE_COIN;
        }

        @ContractSetupParameter
        default String tarascaRs() { return "ARDOR-9SJS-TS84-Q293-7J6TE"; }

        @ContractSetupParameter
        default int priceGiftz() {
            return 1;
        }

        @ContractSetupParameter
        default int cardsPerPack() {
            return 3;
        }

        @ContractSetupParameter
        default String collectionRs() {
            return "ARDOR-4V3B-TVQA-Q6LF-GMH3T";
        }

        @ContractSetupParameter
        default String validCurrency() {
            return "9375231913536683768";
        }
    }
}
