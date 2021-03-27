package com.jelurida.ardor.contracts;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

import nxt.crypto.Crypto;
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
		
    public JO processTransaction(TransactionContext context) {
        if (context.notSameRecipient()) {
            return new JO();
        } else {
            IgnisAssetLottery.Params params = (IgnisAssetLottery.Params)context.getParams(IgnisAssetLottery.Params.class);
            long priceIgnis = ((IgnisAssetLottery.Params)context.getParams(IgnisAssetLottery.Params.class)).priceIgn()*ChildChain.IGNIS.ONE_COIN;
            long tarascaCut = ((IgnisAssetLottery.Params)context.getParams(IgnisAssetLottery.Params.class)).tdaoCut()*ChildChain.IGNIS.ONE_COIN;
            String tarascaRs = ((IgnisAssetLottery.Params)context.getParams(IgnisAssetLottery.Params.class)).tdao();
            int priceGiftz = ((IgnisAssetLottery.Params)context.getParams(IgnisAssetLottery.Params.class)).priceGif();
            String validCurrency = ((IgnisAssetLottery.Params)context.getParams(IgnisAssetLottery.Params.class)).valCur();
            int cardsPerPack = ((IgnisAssetLottery.Params)context.getParams(IgnisAssetLottery.Params.class)).cardsPp();
            String collectionRs = ((IgnisAssetLottery.Params)context.getParams(IgnisAssetLottery.Params.class)).col();
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
                    long tarascaCutTotal = tarascaCut * (long)numPacks;
                    context.logInfoMessage("CONTRACT: paying Tarasca %d Ignis, to %s, on chain %d", tarascaCutTotal / ChildChain.IGNIS.ONE_COIN, tarascaRs,chainId);
                    SendMoneyCall sendMoneyCall = SendMoneyCall.create(chainId).recipient(tarascaRs).amountNQT(tarascaCutTotal);
                    context.createTransaction(sendMoneyCall);
                } else {
                    context.logInfoMessage("CONTRACT: not paying Tarasca any Ignis");
                }

                JO accAssets = GetAccountAssetsCall.create().account(accountRs).call();
                JA accountAssets = accAssets.getArray("accountAssets");
                JO cAssets = GetAssetsByIssuerCall.create().account(collectionRs).call();
                JA schachtel = cAssets.getArray("assets");
                JA collectionAssets = new JA(schachtel.getObject(0));

                JO contractParameters = context.getContractRunnerConfigParams(getClass().getSimpleName());

                String secretForRandomString = "0";

                if(contractParameters.isExist("secretForRandomString")) {
                    secretForRandomString = contractParameters.getString("secretForRandomString");
                }

                MessageDigest digest = Crypto.sha256();

                digest.update(secretForRandomString.getBytes(StandardCharsets.UTF_8));
                digest.update(ByteBuffer.allocate(Long.BYTES).putLong(context.getTransaction().getBlockId()).array());
                digest.update(context.getTransaction().getFullHash());

                // random seed derived from long from HASH(secretForRandomString | blockId | transactionFullHash)
                context.initRandom(longLsbFromBytes(digest.digest()));

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
        Map<String, Long> assetWeights = collectionAssets.objects().stream().collect(
                Collectors.toMap(
                        (asset) -> {
                            return asset.getString("asset");
                        }, (asset) -> {
                            // base weight on rarity attribute of the card description:
                            JO cardDescription = JO.parse(asset.getString("description"));
                            String rarity = cardDescription.getString("rarity");
                            Long weight = 0L;
                            switch (rarity) {
                                case "very rare":
                                    weight = 20000L;
                                    break;
                                case "rare":
                                    weight = 50000L;
                                    break;
                                case "common":
                                    weight = 100000L;
                                    break;
                                default:
                                    weight = 100000L;
                                    break;
                            }

                            // make sure that assetID is available in the contract account. the asset ID should be unique (list of size 1)
                            List<JO> aAssets = accountAssets.objects().stream().filter(
                                    a -> (asset.getString("asset").equals(a.getString("asset")) )).collect(Collectors.toList());
                            boolean isAvailable = aAssets.get(0).getLong("quantityQNT") > 0;

                            if (isAvailable) {
                              return weight;
                            }
                            else {
                              return 0L;
                            }
                        }
                )
        );
        return assetWeights;
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
        long amount = 0;

        if(response.getChainId() == 2) {
            amount = response.getAmount();
        }

        long boughtPacks = amount / priceIgnis;
        return boughtPacks >= 1L ? (int)boughtPacks : 0;
    }

    private void packCards(JO AccountAssets) {
    }

    public static long longLsbFromBytes(byte[] bytes) {
        BigInteger bi = new BigInteger(1, new byte[] {bytes[7], bytes[6], bytes[5], bytes[4], bytes[3], bytes[2], bytes[1], bytes[0]});
        return bi.longValue();
    }

    @ContractParametersProvider
    public interface Params {
        @ContractSetupParameter
        default long priceIgn() {
            return 99L;
        }

        @ContractSetupParameter
        default long tdaoCut() {
            return 50L;
        }

        @ContractSetupParameter
        default String tdao() { return "ARDOR-9SJS-TS84-Q293-7J6TE"; }

        @ContractSetupParameter
        default int priceGif() {
            return 1;
        }

        @ContractSetupParameter
        default int cardsPp() {
            return 3;
        }

        @ContractSetupParameter
        default String col() {
            return "ARDOR-4V3B-TVQA-Q6LF-GMH3T";
        }

        @ContractSetupParameter
        default String valCur() {
            return "9375231913536683768";
        }
    }
}
