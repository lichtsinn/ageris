/*
 * Copyright 2026 Mario Lichtsinn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.lichtsinn.ageris.openfeature;

import com.fasterxml.jackson.databind.JsonNode;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import io.github.lichtsinn.ageris.core.ConfigurationListener;
import io.github.lichtsinn.ageris.core.ConfigurationLoader;
import io.github.lichtsinn.ageris.core.ConfigurationStatus;
import io.github.lichtsinn.ageris.core.ConfigurationUpdate;
import io.github.lichtsinn.ageris.core.Evaluation;
import io.github.lichtsinn.ageris.core.FeatureFlagManager;
import io.github.lichtsinn.ageris.core.JsonConfigManager;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Lets an application use Ageris through the OpenFeature API. Boolean lookups are answered from the feature flags, and
 * text, number and object lookups from the JSON configs. Configs of a class of the application, and policies, are not
 * reachable this way; they stay on the config manager of Ageris.
 *
 * The application sets up Ageris as usual and hands over the managers, and usually the loader:
 *
 *  OpenFeatureAPI.getInstance().setProviderAndWait(new AgerisProvider(featureFlagManager, jsonConfigManager, loader));
 *
 * With a loader, the provider starts it when OpenFeature initializes the provider, waits for the first load, and shuts it
 * down when OpenFeature shuts the provider down. Without one it starts and stops nothing.
 */
public final class AgerisProvider extends EventProvider {

    private static final String NAME = "Ageris";
    private static final String DEFAULT_KEY_TYPE = "user";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final FeatureFlagManager flags;
    private final JsonConfigManager configs;
    private final ConfigurationLoader loader;
    private final String keyType;
    private final Duration timeout;

    private final AtomicBoolean started = new AtomicBoolean();
    private final Object lock = new Object();
    private final ConfigurationListener listener = new Forwarder();

    /* Both guarded by the lock. */
    private boolean reporting;
    private boolean stale;

    /**
     * Creates a provider that starts and stops the loader.
     *
     * @param featureFlagManager manager that answers boolean lookups
     * @param jsonConfigManager manager that answers text, number and object lookups
     * @param loader loader of the two managers, not started yet
     */
    public AgerisProvider(FeatureFlagManager featureFlagManager, JsonConfigManager jsonConfigManager, ConfigurationLoader loader) {
        this(builder(featureFlagManager, jsonConfigManager).loader(Objects.requireNonNull(loader, "loader")));
    }

    /**
     * Creates a provider without a loader, for the managers of InMemoryAgeris in tests, and for an application that
     * starts and stops the loader itself.
     *
     * @param featureFlagManager manager that answers boolean lookups
     * @param jsonConfigManager manager that answers text, number and object lookups
     */
    public AgerisProvider(FeatureFlagManager featureFlagManager, JsonConfigManager jsonConfigManager) {
        this(builder(featureFlagManager, jsonConfigManager));
    }

    private AgerisProvider(Builder builder) {
        super();
        this.flags = builder.flags;
        this.configs = builder.configs;
        this.loader = builder.loader;
        this.keyType = builder.keyType;
        this.timeout = builder.timeout;
    }

    /**
     * @param featureFlagManager manager that answers boolean lookups
     * @param jsonConfigManager manager that answers text, number and object lookups
     * @return builder for a provider with other settings than the defaults
     */
    public static Builder builder(FeatureFlagManager featureFlagManager, JsonConfigManager jsonConfigManager) {
        return new Builder(Objects.requireNonNull(featureFlagManager, "featureFlagManager"), Objects.requireNonNull(jsonConfigManager, "jsonConfigManager"));
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    /**
     * Starts the loader, if the provider has one, and waits for the first load. Does nothing when called again.
     *
     * @param evaluationContext global evaluation context, not used
     * @throws GeneralError if the first load fails or takes longer than the provider was told to wait
     */
    @Override
    public void initialize(EvaluationContext evaluationContext) {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        flags.addListener(listener);
        configs.addListener(listener);
        if (loader != null) {
            loader.start();
            if (!loader.awaitFirstLoad(timeout)) {
                throw new GeneralError(whyNotLoaded());
            }
        }
        synchronized (lock) {
            reporting = true;
        }
    }

    /**
     * Stops listening to the managers and shuts the loader down, if the provider has one.
     */
    @Override
    public void shutdown() {
        synchronized (lock) {
            reporting = false;
        }
        flags.removeListener(listener);
        configs.removeListener(listener);
        if (loader != null && started.get()) {
            loader.shutdown();
        }
        super.shutdown();
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext context) {
        final Evaluation<Boolean> evaluation = flags.evaluate(key, ContextMapping.toAgeris(context, keyType));
        if (evaluation.getReason() == Evaluation.Reason.NOT_FOUND) {
            return failed(defaultValue, ErrorCode.FLAG_NOT_FOUND, "No feature flag named " + key);
        }
        return found(evaluation.getValue(), evaluation.getReason());
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext context) {
        return config(key, defaultValue, context, "a text", JsonNode::isTextual, JsonNode::textValue);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext context) {
        return config(key, defaultValue, context, "a whole number", json -> json.isIntegralNumber() && json.canConvertToInt(), JsonNode::intValue);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext context) {
        return config(key, defaultValue, context, "a number", JsonNode::isNumber, JsonNode::doubleValue);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext context) {
        return config(key, defaultValue, context, "anything", json -> true, JsonValues::toValue);
    }

    private <T> ProviderEvaluation<T> config(String key, T defaultValue, EvaluationContext context, String wanted,
                                             Predicate<JsonNode> fits, Function<JsonNode, T> read) {
        final Evaluation<JsonNode> evaluation = configs.evaluate(key, ContextMapping.toAgeris(context, keyType));
        if (evaluation.getReason() == Evaluation.Reason.NOT_FOUND) {
            return failed(defaultValue, ErrorCode.FLAG_NOT_FOUND, "No JSON config named " + key);
        }
        final JsonNode json = evaluation.getValue();
        if (!fits.test(json)) {
            return failed(defaultValue, ErrorCode.TYPE_MISMATCH,
                    "JSON config " + key + " is not " + wanted + " but " + json.getNodeType().name().toLowerCase(java.util.Locale.ROOT) + ": " + json);
        }
        return found(read.apply(json), evaluation.getReason());
    }

    private static <T> ProviderEvaluation<T> found(T value, Evaluation.Reason reason) {
        Reason answer = Reason.TARGETING_MATCH;
        if (reason == Evaluation.Reason.STATIC) {
            answer = Reason.STATIC;
        } else if (reason == Evaluation.Reason.DEFAULT) {
            answer = Reason.DEFAULT;
        }
        return ProviderEvaluation.<T>builder().value(value).reason(answer.toString()).build();
    }

    private static <T> ProviderEvaluation<T> failed(T defaultValue, ErrorCode code, String message) {
        return ProviderEvaluation.<T>builder().value(defaultValue).reason(Reason.ERROR.toString()).errorCode(code).errorMessage(message).build();
    }

    private String whyNotLoaded() {
        final StringBuilder why = new StringBuilder();
        for (final ConfigurationStatus status : new ConfigurationStatus[] {flags.getStatus(), configs.getStatus()}) {
            if (status.getFailure() != null) {
                why.append(why.length() == 0 ? "" : "; ").append(status.getFailure().getMessage());
            }
        }
        return why.length() == 0 ? "Not loaded within " + timeout : why.toString();
    }

    private static boolean isStale(ConfigurationStatus status) {
        return status.getState() == ConfigurationStatus.State.STALE;
    }

    /**
     * Passes on to OpenFeature what happens in the two managers.
     */
    private final class Forwarder implements ConfigurationListener {

        @Override
        public void onUpdate(ConfigurationUpdate update) {
            synchronized (lock) {
                if (reporting) {
                    emitProviderConfigurationChanged(ProviderEventDetails.builder().flagsChanged(update.getConfigurationNames()).build());
                }
            }
        }

        @Override
        public void onStatusChange(ConfigurationStatus status) {
            synchronized (lock) {
                final boolean staleNow = isStale(flags.getStatus()) || isStale(configs.getStatus());
                if (reporting && staleNow && !stale) {
                    final Exception failure = status.getFailure();
                    emitProviderStale(ProviderEventDetails.builder().message(failure == null ? null : failure.getMessage()).build());
                } else if (reporting && !staleNow && stale) {
                    emitProviderReady(ProviderEventDetails.builder().build());
                }
                stale = staleNow;
            }
        }
    }

    /**
     * Builder for a provider with other settings than the defaults.
     */
    public static final class Builder {

        private final FeatureFlagManager flags;
        private final JsonConfigManager configs;
        private ConfigurationLoader loader;
        private String keyType = DEFAULT_KEY_TYPE;
        private Duration timeout = DEFAULT_TIMEOUT;

        private Builder(FeatureFlagManager flags, JsonConfigManager configs) {
            this.flags = flags;
            this.configs = configs;
        }

        /**
         * @param loader loader of the two managers, not started yet; the provider starts and stops it
         * @return this builder
         */
        public Builder loader(ConfigurationLoader loader) {
            this.loader = loader;
            return this;
        }

        /**
         * @param type context type the targeting key of a caller is put under; "user" unless set
         * @return this builder
         */
        public Builder targetingKeyType(String type) {
            this.keyType = Objects.requireNonNull(type, "type");
            return this;
        }

        /**
         * @param timeout how long initialization waits for the first load; 30 seconds unless set
         * @return this builder
         */
        public Builder firstLoadTimeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        /**
         * @return the provider
         */
        public AgerisProvider build() {
            return new AgerisProvider(this);
        }
    }
}
