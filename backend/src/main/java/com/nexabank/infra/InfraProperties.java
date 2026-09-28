package com.nexabank.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized Phase 7 infrastructure configuration (all configurable, no
 * hard-coded secrets or limits).
 *
 * <pre>
 * app:
 *   redis: {enabled, host, port, password, connect-timeout, ...}
 *   kafka: {enabled, bootstrap-servers, ...}
 *   rate-limit: {enabled, default-max-requests, window, rules[...]}
 *   outbox: {publisher-enabled, batch-size, max-retries, poll-delay}
 * </pre>
 */
@ConfigurationProperties(prefix = "app")
public class InfraProperties {

    private final Redis redis = new Redis();
    private final Kafka kafka = new Kafka();
    private final RateLimit rateLimit = new RateLimit();
    private final Outbox outbox = new Outbox();

    public Redis getRedis() {
        return redis;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public static class Redis {
        /** Master switch. false = in-memory fallbacks, app runs without Redis. */
        private boolean enabled = false;
        private String host = "localhost";
        private int port = 6379;
        private String password = "";
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration commandTimeout = Duration.ofSeconds(2);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getCommandTimeout() {
            return commandTimeout;
        }

        public void setCommandTimeout(Duration commandTimeout) {
            this.commandTimeout = commandTimeout;
        }
    }

    public static class Kafka {
        /** Master switch. false = no broker needed; outbox rows stay pending. */
        private boolean enabled = false;
        private String bootstrapServers = "localhost:9092";
        private String transactionTopic = "nexabank.transactions";
        private String transferTopic = "nexabank.transfers";
        private String fraudEvaluationTopic = "nexabank.fraud-evaluations";
        private String fraudAlertTopic = "nexabank.fraud-alerts";
        private String consumerGroup = "nexabank-analytics";
        /** Producer upper bound for a single send. */
        private Duration deliveryTimeout = Duration.ofSeconds(120);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public String getTransactionTopic() {
            return transactionTopic;
        }

        public void setTransactionTopic(String transactionTopic) {
            this.transactionTopic = transactionTopic;
        }

        public String getTransferTopic() {
            return transferTopic;
        }

        public void setTransferTopic(String transferTopic) {
            this.transferTopic = transferTopic;
        }

        public String getFraudEvaluationTopic() {
            return fraudEvaluationTopic;
        }

        public void setFraudEvaluationTopic(String fraudEvaluationTopic) {
            this.fraudEvaluationTopic = fraudEvaluationTopic;
        }

        public String getFraudAlertTopic() {
            return fraudAlertTopic;
        }

        public void setFraudAlertTopic(String fraudAlertTopic) {
            this.fraudAlertTopic = fraudAlertTopic;
        }

        public String getConsumerGroup() {
            return consumerGroup;
        }

        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }

        public Duration getDeliveryTimeout() {
            return deliveryTimeout;
        }

        public void setDeliveryTimeout(Duration deliveryTimeout) {
            this.deliveryTimeout = deliveryTimeout;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private int defaultMaxRequests = 200;
        private Duration defaultWindow = Duration.ofMinutes(1);
        private List<Rule> rules = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDefaultMaxRequests() {
            return defaultMaxRequests;
        }

        public void setDefaultMaxRequests(int defaultMaxRequests) {
            this.defaultMaxRequests = defaultMaxRequests;
        }

        public Duration getDefaultWindow() {
            return defaultWindow;
        }

        public void setDefaultWindow(Duration defaultWindow) {
            this.defaultWindow = defaultWindow;
        }

        public List<Rule> getRules() {
            return rules;
        }

        public void setRules(List<Rule> rules) {
            this.rules = rules;
        }

        public static class Rule {
            /** Request path prefix this rule applies to. */
            private String pathPrefix = "/api/v1/auth/login";
            private int maxRequests = 20;
            private Duration window = Duration.ofMinutes(1);

            public String getPathPrefix() {
                return pathPrefix;
            }

            public void setPathPrefix(String pathPrefix) {
                this.pathPrefix = pathPrefix;
            }

            public int getMaxRequests() {
                return maxRequests;
            }

            public void setMaxRequests(int maxRequests) {
                this.maxRequests = maxRequests;
            }

            public Duration getWindow() {
                return window;
            }

            public void setWindow(Duration window) {
                this.window = window;
            }
        }
    }

    public static class Outbox {
        private boolean publisherEnabled = true;
        private int batchSize = 50;
        private int maxRetries = 20;
        private Duration pollDelay = Duration.ofSeconds(5);

        public boolean isPublisherEnabled() {
            return publisherEnabled;
        }

        public void setPublisherEnabled(boolean publisherEnabled) {
            this.publisherEnabled = publisherEnabled;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public Duration getPollDelay() {
            return pollDelay;
        }

        public void setPollDelay(Duration pollDelay) {
            this.pollDelay = pollDelay;
        }
    }
}
