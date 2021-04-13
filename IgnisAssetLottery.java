package com.jelurida.ardor.contracts;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

import nxt.addons.*;
import nxt.crypto.Crypto;
import nxt.blockchain.TransactionType;
import nxt.http.callers.GetAccountAssetsCall;
import nxt.http.callers.GetAssetsByIssuerCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.TransferAssetCall;
import nxt.http.responses.TransactionResponse;

import static nxt.blockchain.ChildChain.IGNIS;


public class IgnisAssetLottery extends AbstractContract {

    @ValidateContractRunnerIsRecipient
    @ValidateChain(accept = 2)
    public JO processTransaction(TransactionContext context) {
        if (context.notSameRecipient()) {
            return new JO();
        } else {
            IgnisAssetLottery.ContractParams contractParams = context.getParams(IgnisAssetLottery.ContractParams.class);
            String tarascaRs = contractParams.tdao();
            String cardRs = contractParams.tcard();
            String validCurrency = contractParams.valCur();
            String collectionRs = contractParams.col();// context.getAccountRs();

            IgnisAssetLottery.RunnerParams runnerParams = context.getParams(IgnisAssetLottery.RunnerParams.class);
            long priceIgnis = runnerParams.priceIgnis()*IGNIS.ONE_COIN;
            long tarascaCut = runnerParams.tarascaCut()*IGNIS.ONE_COIN;
            long cardCut = runnerParams.cardCut()*IGNIS.ONE_COIN;

            int priceGiftz = runnerParams.priceGiftz();
            int cardsPerPack = runnerParams.cardsPerPack();
            int maxPacks = runnerParams.maxPacks();

            // other parameters:
            String accountRs = context.getAccountRs();
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
                    context.logInfoMessage("CONTRACT: paying Tarasca %d Ignis, to %s, on chain %d", tarascaCutTotal / IGNIS.ONE_COIN, tarascaRs,chainId);
                    JO message = new JO();
                    message.put("triggerTransaction",context.getTransaction().getTransactionId());
                    message.put("reason","payment");
                    SendMoneyCall sendMoneyTarasca = SendMoneyCall.create(chainId).recipient(tarascaRs).amountNQT(tarascaCutTotal).message(message.toJSONString()).messageIsText(true).messageIsPrunable(true);
                    context.createTransaction(sendMoneyTarasca);

                    long cardCutTotal = cardCut * (long)numPacks;
                    context.logInfoMessage("CONTRACT: paying Tarasca Card holders %d Ignis, to %s, on chain %d", cardCutTotal / IGNIS.ONE_COIN, cardRs,chainId);
                    SendMoneyCall sendMoneyCards = SendMoneyCall.create(chainId).recipient(cardRs).amountNQT(cardCutTotal).message(message.toJSONString()).messageIsText(true).messageIsPrunable(true);
                    context.createTransaction(sendMoneyCards);

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
                        String asset = distributedRandomNumberGenerator.processInvocation(delegatedContext, assetsForDraw);
                        Integer curValue = pack.putIfAbsent(asset, 1);
                        if (curValue != null) {
                            pack.put(asset, curValue + 1);
                        }

                        context.logInfoMessage("CONTRACT: selected asset: %s", asset);
                    }
                }

                JO message = new JO();
                message.put("triggerTransaction",context.getTransaction().getTransactionId());
                message.put("reason","boosterPack");

                pack.forEach((assetx, quantity) -> {
                    TransferAssetCall transferAsset = TransferAssetCall.create(chainId).recipient(triggerTransaction.getSenderRs()).asset(assetx).quantityQNT((long)quantity).message(message.toJSONString()).messageIsText(true).messageIsPrunable(true);
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
                                    weight = 2500L;
                                    break;
                                case "rare":
                                    weight = 5000L;
                                    break;
                                case "common":
                                    weight = 10000L;
                                    break;
                                default:
                                    weight = 10000L;
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


    public static long longLsbFromBytes(byte[] bytes) {
        BigInteger bi = new BigInteger(1, new byte[] {bytes[7], bytes[6], bytes[5], bytes[4], bytes[3], bytes[2], bytes[1], bytes[0]});
        return bi.longValue();
    }


    @ContractParametersProvider
    public interface ContractParams {

        @ContractSetupParameter
        default String tdao() { return "ARDOR-WDYX-3Q5N-K4HS-CUTKE"; }

        @ContractSetupParameter
        default String tcard() { return "ARDOR-5NCL-DRBZ-XBWF-DDN5T"; }

        @ContractSetupParameter
        default String col() {
            return "ARDOR-4V3B-TVQA-Q6LF-GMH3T";
        }

        @ContractSetupParameter
        default String valCur() {
            return "9375231913536683768";
        }
    }

    @ContractParametersProvider
    public interface RunnerParams {

        @ContractSetupParameter
        default long priceIgnis() { return 99L; }

        @ContractSetupParameter
        default long tarascaCut() { return 40L; }

        @ContractSetupParameter
        default long cardCut() { return 10L; }

        @ContractSetupParameter
        default int priceGiftz() { return 1; }

        @ContractSetupParameter
        default int cardsPerPack() { return 3; }

        @ContractSetupParameter
        default int maxPacks(){ return 5; }
    }
}
