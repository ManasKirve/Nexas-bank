package com.nexabank.fraud;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Centralized fraud rule configuration bound from {@code application.yml}.
 *
 * <p>No magic numbers live inside individual rules — every threshold and
 * score contribution is a property here with educational defaults. Example:</p>
 * <pre>
 * fraud:
 *   evaluation-version: v1
 *   review-threshold: 30
 *   block-threshold: 60
 *   rules:
 *     large-transaction-threshold: 50000
 *     ...
 * </pre>
 */
@ConfigurationProperties(prefix = "fraud")
public class FraudProperties {

    /** Rule-set version stamped on every evaluation for historical traceability. */
    private String evaluationVersion = "v1";

    /** Score at/above which the decision becomes REVIEW. */
    private int reviewThreshold = 30;

    /** Score at/above which the decision becomes BLOCK. */
    private int blockThreshold = 60;

    private final Rules rules = new Rules();

    public String getEvaluationVersion() {
        return evaluationVersion;
    }

    public void setEvaluationVersion(String evaluationVersion) {
        this.evaluationVersion = evaluationVersion;
    }

    public int getReviewThreshold() {
        return reviewThreshold;
    }

    public void setReviewThreshold(int reviewThreshold) {
        this.reviewThreshold = reviewThreshold;
    }

    public int getBlockThreshold() {
        return blockThreshold;
    }

    public void setBlockThreshold(int blockThreshold) {
        this.blockThreshold = blockThreshold;
    }

    public Rules getRules() {
        return rules;
    }

    public static class Rules {
        private BigDecimal largeTransactionThreshold = new BigDecimal("50000");
        private int largeTransactionScore = 25;

        private int highFrequencyCount = 5;
        private int highFrequencyWindowMinutes = 10;
        private int highFrequencyScore = 20;

        private int rapidWithdrawalCount = 3;
        private int rapidWithdrawalWindowMinutes = 10;
        private int rapidWithdrawalScore = 20;

        private int rapidTransferCount = 3;
        private int rapidTransferWindowMinutes = 10;
        private int rapidTransferScore = 20;

        private int newBeneficiaryWindowHours = 24;
        private int newBeneficiaryScore = 15;

        private int activitySpikeScore = 20;
        private double activitySpikeMultiplier = 2.0;
        private int activitySpikeMinHistory = 3;
        private int activitySpikeLookback = 10;

        public BigDecimal getLargeTransactionThreshold() {
            return largeTransactionThreshold;
        }

        public void setLargeTransactionThreshold(BigDecimal largeTransactionThreshold) {
            this.largeTransactionThreshold = largeTransactionThreshold;
        }

        public int getLargeTransactionScore() {
            return largeTransactionScore;
        }

        public void setLargeTransactionScore(int largeTransactionScore) {
            this.largeTransactionScore = largeTransactionScore;
        }

        public int getHighFrequencyCount() {
            return highFrequencyCount;
        }

        public void setHighFrequencyCount(int highFrequencyCount) {
            this.highFrequencyCount = highFrequencyCount;
        }

        public int getHighFrequencyWindowMinutes() {
            return highFrequencyWindowMinutes;
        }

        public void setHighFrequencyWindowMinutes(int highFrequencyWindowMinutes) {
            this.highFrequencyWindowMinutes = highFrequencyWindowMinutes;
        }

        public int getHighFrequencyScore() {
            return highFrequencyScore;
        }

        public void setHighFrequencyScore(int highFrequencyScore) {
            this.highFrequencyScore = highFrequencyScore;
        }

        public int getRapidWithdrawalCount() {
            return rapidWithdrawalCount;
        }

        public void setRapidWithdrawalCount(int rapidWithdrawalCount) {
            this.rapidWithdrawalCount = rapidWithdrawalCount;
        }

        public int getRapidWithdrawalWindowMinutes() {
            return rapidWithdrawalWindowMinutes;
        }

        public void setRapidWithdrawalWindowMinutes(int rapidWithdrawalWindowMinutes) {
            this.rapidWithdrawalWindowMinutes = rapidWithdrawalWindowMinutes;
        }

        public int getRapidWithdrawalScore() {
            return rapidWithdrawalScore;
        }

        public void setRapidWithdrawalScore(int rapidWithdrawalScore) {
            this.rapidWithdrawalScore = rapidWithdrawalScore;
        }

        public int getRapidTransferCount() {
            return rapidTransferCount;
        }

        public void setRapidTransferCount(int rapidTransferCount) {
            this.rapidTransferCount = rapidTransferCount;
        }

        public int getRapidTransferWindowMinutes() {
            return rapidTransferWindowMinutes;
        }

        public void setRapidTransferWindowMinutes(int rapidTransferWindowMinutes) {
            this.rapidTransferWindowMinutes = rapidTransferWindowMinutes;
        }

        public int getRapidTransferScore() {
            return rapidTransferScore;
        }

        public void setRapidTransferScore(int rapidTransferScore) {
            this.rapidTransferScore = rapidTransferScore;
        }

        public int getNewBeneficiaryWindowHours() {
            return newBeneficiaryWindowHours;
        }

        public void setNewBeneficiaryWindowHours(int newBeneficiaryWindowHours) {
            this.newBeneficiaryWindowHours = newBeneficiaryWindowHours;
        }

        public int getNewBeneficiaryScore() {
            return newBeneficiaryScore;
        }

        public void setNewBeneficiaryScore(int newBeneficiaryScore) {
            this.newBeneficiaryScore = newBeneficiaryScore;
        }

        public int getActivitySpikeScore() {
            return activitySpikeScore;
        }

        public void setActivitySpikeScore(int activitySpikeScore) {
            this.activitySpikeScore = activitySpikeScore;
        }

        public double getActivitySpikeMultiplier() {
            return activitySpikeMultiplier;
        }

        public void setActivitySpikeMultiplier(double activitySpikeMultiplier) {
            this.activitySpikeMultiplier = activitySpikeMultiplier;
        }

        public int getActivitySpikeMinHistory() {
            return activitySpikeMinHistory;
        }

        public void setActivitySpikeMinHistory(int activitySpikeMinHistory) {
            this.activitySpikeMinHistory = activitySpikeMinHistory;
        }

        public int getActivitySpikeLookback() {
            return activitySpikeLookback;
        }

        public void setActivitySpikeLookback(int activitySpikeLookback) {
            this.activitySpikeLookback = activitySpikeLookback;
        }
    }
}
