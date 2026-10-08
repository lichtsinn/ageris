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
package io.github.lichtsinn.ageris.core;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import io.github.lichtsinn.ageris.core.common.EnvironmentConfigurationContext;
import io.github.lichtsinn.ageris.core.configs.DiscountConfig;
import io.github.lichtsinn.ageris.core.configs.NamedBindingConfig;
import io.github.lichtsinn.ageris.core.configs.NumberConfig;
import io.github.lichtsinn.ageris.core.configs.PriceSource;
import io.github.lichtsinn.ageris.core.configs.TwoSourceConfig;
import io.github.lichtsinn.ageris.core.metrics.ConfigMetrics;
import io.github.lichtsinn.ageris.core.utils.DefaultClassFinder;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Tests that {@link ConfigurationMapper} hands bindings to config classes and never lets the file supply one.
 */
public class ConfigBindingsTest {

    private static final String PACKAGE = "io.github.lichtsinn.ageris.core.configs.";
    private static final String DISCOUNT = PACKAGE + "DiscountConfig";
    private static final String TWO_SOURCE = PACKAGE + "TwoSourceConfig";
    private static final String NUMBER = PACKAGE + "NumberConfig";
    private static final String NAMED_BINDING = PACKAGE + "NamedBindingConfig";
    private static final String EXPOSED_BINDING = PACKAGE + "ExposedBindingConfig";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConfigMetrics metrics = new ConfigMetrics();
    private final ConfigurationContext none = new ConfigurationContext();
    private final PriceSource prices = new PriceSource(200);
    private final Bindings bindings = new Bindings();

    @Test
    public void testConfigReceivesBindingByType() throws IOException {
        bindings.bind(PriceSource.class, prices);

        DiscountConfig config = (DiscountConfig) read(false, DISCOUNT, "{ \"value\": { \"percent\": 25 } }").get(DISCOUNT).getValue(none);

        Assert.assertEquals(25, config.getPercent());
        Assert.assertSame(prices, config.prices());
        Assert.assertEquals(150, config.discountedPrice());
    }

    @Test
    public void testConfigReceivesBindingsByTypeAndByName() throws IOException {
        PriceSource backup = new PriceSource(300);
        bindings.bind(PriceSource.class, prices);
        bindings.bind("backupPrices", backup);

        TwoSourceConfig config = (TwoSourceConfig) read(false, TWO_SOURCE, "{ \"value\": { \"percent\": 5 } }").get(TWO_SOURCE).getValue(none);

        Assert.assertSame(prices, config.primary());
        Assert.assertSame(backup, config.backup());
    }

    @Test
    public void testEveryValueInTheModifierTreeReceivesTheBinding() throws IOException {
        bindings.bind(PriceSource.class, prices);
        String entry = "{ \"value\": { \"percent\": 25 }, \"modifiers\": { \"type\": \"environment\", \"contexts\": {"
                + " \"qa\": { \"value\": { \"percent\": 50 } } } } }";

        Configuration<Object> configuration = read(false, DISCOUNT, entry).get(DISCOUNT);
        DiscountConfig byDefault = (DiscountConfig) configuration.getValue(none);
        DiscountConfig inQa = (DiscountConfig) configuration.getValue(new EnvironmentConfigurationContext("qa"));

        Assert.assertEquals(150, byDefault.discountedPrice());
        Assert.assertEquals(100, inQa.discountedPrice());
        Assert.assertSame(prices, byDefault.prices());
        Assert.assertSame(prices, inQa.prices());
    }

    @Test
    public void testEveryReloadBuildsNewConfigsHoldingTheSameBinding() throws IOException {
        bindings.bind(PriceSource.class, prices);
        ConfigurationMapper<Object> mapper = mapper(false);
        String content = configs(DISCOUNT, "{ \"value\": { \"percent\": 25 } }");

        DiscountConfig first = (DiscountConfig) mapper.readValue(content).get(DISCOUNT).getValue(none);
        DiscountConfig second = (DiscountConfig) mapper.readValue(content).get(DISCOUNT).getValue(none);

        Assert.assertNotSame(first, second);
        Assert.assertSame(first.prices(), second.prices());
    }

    @Test
    public void testMissingBindingIsSkippedAndCountedWhenSkipping() throws IOException {
        Map<String, Configuration<Object>> configurations = read(true, DISCOUNT, "{ \"value\": { \"percent\": 25 } }");

        Assert.assertEquals(Collections.emptyMap(), configurations);
        Assert.assertEquals(1, metrics.getConfigNonInstantiableSkips());
    }

    @Test
    public void testMissingBindingFailsTheUpdateNamingBindingAndConfigWhenNotSkipping() {
        try {
            read(false, DISCOUNT, "{ \"value\": { \"percent\": 25 } }");
            Assert.fail("A config whose binding is missing must not load.");
        } catch (IOException exception) {
            String messages = messages(exception);
            Assert.assertTrue(messages, messages.contains(PACKAGE + "PriceSource"));
            Assert.assertTrue(messages, messages.contains(DISCOUNT));
        }
    }

    @Test(expected = IOException.class)
    public void testFilePropertyAimedAtAnUnnamedBindingIsRejected() throws IOException {
        bindings.bind(PriceSource.class, prices);

        read(false, DISCOUNT, "{ \"value\": { \"percent\": 25, \"prices\": { \"basePrice\": 1 } } }");
    }

    @Test
    public void testConfigWithoutBindingsLoadsAsBefore() throws IOException {
        NumberConfig config = (NumberConfig) read(false, NUMBER, "{ \"value\": { \"number\": 7 } }").get(NUMBER).getValue(none);

        Assert.assertEquals(7, config.getNumber());
    }

    @Test
    public void testClassThatNamesItsBindingAsFilePropertyIsRefusedEvenWhenTheFileIsSilent() {
        bindings.bind(PriceSource.class, prices);
        try {
            read(false, NAMED_BINDING, "{ \"value\": { \"percent\": 25 } }");
            Assert.fail("A class whose binding the file could supply must not load.");
        } catch (IOException exception) {
            String messages = messages(exception);
            Assert.assertTrue(messages, messages.contains("prices"));
            Assert.assertTrue(messages, messages.contains(NAMED_BINDING));
        }
    }

    @Test(expected = IOException.class)
    public void testFileCannotReplaceABindingTheClassNamed() throws IOException {
        bindings.bind(PriceSource.class, prices);

        read(false, NAMED_BINDING, "{ \"value\": { \"percent\": 25, \"prices\": { \"basePrice\": 1 } } }");
    }

    @Test
    public void testClassThatNamesItsBindingIsSkippedAndCountedWhenSkipping() throws IOException {
        bindings.bind(PriceSource.class, prices);

        Map<String, Configuration<Object>> configurations = read(true, NAMED_BINDING, "{ \"value\": { \"percent\": 25 } }");

        Assert.assertEquals(Collections.emptyMap(), configurations);
        Assert.assertEquals(1, metrics.getConfigNonInstantiableSkips());
    }

    /**
     * A getter for the binding makes Jackson treat the private field behind it as writable from the file.
     */
    @Test
    public void testClassWhoseBindingIsWritableOutsideItsConstructorIsRefusedEvenWhenTheFileIsSilent() {
        bindings.bind(PriceSource.class, prices);
        try {
            read(false, EXPOSED_BINDING, "{ \"value\": { \"percent\": 25 } }");
            Assert.fail("A class with bindings that the file can write to outside its constructor must not load.");
        } catch (IOException exception) {
            String messages = messages(exception);
            Assert.assertTrue(messages, messages.contains("prices"));
            Assert.assertTrue(messages, messages.contains(EXPOSED_BINDING));
        }
    }

    @Test(expected = IOException.class)
    public void testFileCannotReplaceABindingExposedThroughAGetter() throws IOException {
        bindings.bind(PriceSource.class, prices);

        read(false, EXPOSED_BINDING, "{ \"value\": { \"percent\": 25, \"prices\": { \"basePrice\": 1 } } }");
    }

    @Test
    public void testBindingBehindAnIgnoredGetterIsAccepted() throws IOException {
        bindings.bind(PriceSource.class, prices);
        String name = IgnoredGetterConfig.class.getName();

        IgnoredGetterConfig config = (IgnoredGetterConfig) read(false, name, "{ \"value\": { \"percent\": 25 } }").get(name).getValue(none);

        Assert.assertSame(prices, config.getPrices());
        Assert.assertEquals(25, config.getPercent());
    }

    @Test
    public void testCallersObjectMapperIsLeftAsItWas() throws IOException {
        bindings.bind(PriceSource.class, prices);
        read(true, NAMED_BINDING, "{ \"value\": { \"percent\": 25 } }");

        Assert.assertNull(objectMapper.getInjectableValues());
        /* The guard lives on the mapper's private copy only: the caller's own mapper still reads the class. */
        NamedBindingConfig read = objectMapper.reader(new InjectableValues.Std().addValue(PriceSource.class, prices))
                .forType(NamedBindingConfig.class).readValue("{ \"percent\": 25 }");
        Assert.assertEquals(25, read.percent());
    }

    @Test(expected = IllegalStateException.class)
    public void testObjectMapperSubclassThatCannotBeCopiedFailsWhenTheMapperIsBuilt() {
        ObjectMapper notCopyable = new ObjectMapper() { };

        new ConfigurationMapper<>("configs", true, notCopyable, metrics, new DefaultClassFinder(), bindings);
    }

    /**
     * A binding is any object: here a plain value, a supplier and a factory. The config holds one shared
     * instance of each, and gets something new on every call by asking the supplier or the factory.
     */
    @Test
    public void testConfigReceivesAPlainValueASupplierAndAFactory() throws IOException {
        AtomicInteger counter = new AtomicInteger();
        bindings.bind("region", "eu");
        bindings.bind("idSupplier", (Supplier<String>) () -> "id" + counter.incrementAndGet());
        bindings.bind(PriceSourceFactory.class, PriceSource::new);
        String name = FactoryConfig.class.getName();

        FactoryConfig config = (FactoryConfig) read(false, name, "{ \"value\": { \"basePrice\": 40 } }").get(name).getValue(none);

        Assert.assertEquals("eu-id1", config.nextId());
        Assert.assertEquals("eu-id2", config.nextId());
        PriceSource first = config.newSource();
        PriceSource second = config.newSource();
        Assert.assertNotSame(first, second);
        Assert.assertEquals(40, first.basePrice());
        Assert.assertEquals(40, second.basePrice());
    }

    /**
     * Lookup by type uses the raw class, so a supplier bound by type answers a parameter declared with any
     * type argument.
     */
    @Test
    public void testSupplierBoundByTypeIsFoundByItsRawType() throws IOException {
        Supplier<String> supplier = () -> "fixed";
        bindings.bind(Supplier.class, supplier);
        String name = SuppliedConfig.class.getName();

        SuppliedConfig config = (SuppliedConfig) read(false, name, "{ \"value\": { \"prefix\": \"a\" } }").get(name).getValue(none);

        Assert.assertEquals("a-fixed", config.label());
    }

    /**
     * Jackson fills a collection through its getter when there is no setter, which would let the file add
     * to a bound collection that every config shares.
     */
    @Test
    public void testFileCannotAddToABoundCollectionThroughItsGetter() {
        List<PriceSource> sources = new ArrayList<>();
        sources.add(prices);
        bindings.bind("sources", sources);
        String name = BoundListConfig.class.getName();
        try {
            read(false, name, "{ \"value\": { \"priceSources\": [ { \"basePrice\": 1 } ] } }");
            Assert.fail("A class whose bound collection the file can reach through a getter must not load.");
        } catch (IOException exception) {
            Assert.assertTrue(messages(exception), messages(exception).contains("priceSources"));
        }
        Assert.assertEquals(1, sources.size());
    }

    @Test
    public void testConfigWithDeserializerThatUsesTheMapperAsItsCodecStillLoads() throws IOException {
        String name = CodecConfig.class.getName();

        CodecConfig config = (CodecConfig) read(false, name, "{ \"value\": { \"number\": 7 } }").get(name).getValue(none);

        Assert.assertEquals(7, config.number);
    }

    /**
     * With parameter names available (as Jackson's parameter names module provides them), an injected
     * constructor parameter gets a name without the author giving it one. The class must still load.
     */
    @Test
    public void testConfigReceivesItsBindingWhenTheMapperKnowsConstructorParameterNames() throws IOException {
        bindings.bind(PriceSource.class, prices);
        ConfigurationMapper<Object> mapper = new ConfigurationMapper<>("configs", false, mapperKnowingParameterNames(), metrics,
                new DefaultClassFinder(), bindings);

        DiscountConfig config = (DiscountConfig) mapper.readValue(configs(DISCOUNT, "{ \"value\": { \"percent\": 25 } }")).get(DISCOUNT).getValue(none);

        Assert.assertSame(prices, config.prices());
        Assert.assertEquals(25, config.getPercent());
    }

    @Test(expected = IOException.class)
    public void testFileCannotReplaceABindingWhenTheMapperKnowsConstructorParameterNames() throws IOException {
        bindings.bind(PriceSource.class, prices);
        ConfigurationMapper<Object> mapper = new ConfigurationMapper<>("configs", false, mapperKnowingParameterNames(), metrics,
                new DefaultClassFinder(), bindings);

        mapper.readValue(configs(DISCOUNT, "{ \"value\": { \"percent\": 25, \"prices\": { \"basePrice\": 1 } } }"));
    }

    /** Creates a new price source on demand; stands in for any factory an application binds. */
    public interface PriceSourceFactory {

        PriceSource create(int basePrice);
    }

    /** Config with a plain value, a supplier and a factory as its bindings. */
    public static class FactoryConfig {

        private final int basePrice;
        private final String region;
        private final Supplier<String> ids;
        private final PriceSourceFactory factory;

        @JsonCreator
        public FactoryConfig(@JsonProperty("basePrice") int basePrice,
                             @JacksonInject("region") String region,
                             @JacksonInject("idSupplier") Supplier<String> ids,
                             @JacksonInject PriceSourceFactory factory) {
            this.basePrice = basePrice;
            this.region = region;
            this.ids = ids;
            this.factory = factory;
        }

        public String nextId() {
            return region + "-" + ids.get();
        }

        public PriceSource newSource() {
            return factory.create(basePrice);
        }
    }

    /** Config asking for a supplier by type. */
    public static class SuppliedConfig {

        private final String prefix;
        private final Supplier<String> supplier;

        @JsonCreator
        public SuppliedConfig(@JsonProperty("prefix") String prefix, @JacksonInject Supplier<String> supplier) {
            this.prefix = prefix;
            this.supplier = supplier;
        }

        public String label() {
            return prefix + "-" + supplier.get();
        }
    }

    /** Config holding a bound list in an injected field, with a getter Jackson would fill the list through. */
    public static class BoundListConfig {

        @JacksonInject("sources")
        private List<PriceSource> sources;

        public List<PriceSource> getPriceSources() {
            return sources;
        }
    }

    /** Config read by its own deserializer, which relies on the parser's codec being the mapper. */
    @JsonDeserialize(using = CodecConfigDeserializer.class)
    public static class CodecConfig {

        private final int number;

        CodecConfig(int number) {
            this.number = number;
        }
    }

    /** Deserializer using the common idiom of casting the codec to the mapper. */
    public static class CodecConfigDeserializer extends JsonDeserializer<CodecConfig> {

        @Override
        public CodecConfig deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectMapper codec = (ObjectMapper) parser.getCodec();
            JsonNode node = codec.readTree(parser);
            return new CodecConfig(node.get("number").asInt());
        }
    }

    /** Stands in for a mapper with the parameter names module: names the constructor parameters of DiscountConfig. */
    private static ObjectMapper mapperKnowingParameterNames() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
            private static final long serialVersionUID = 1L;

            @Override
            public String findImplicitPropertyName(AnnotatedMember member) {
                if (member instanceof AnnotatedParameter && member.getDeclaringClass() == DiscountConfig.class) {
                    return ((AnnotatedParameter) member).getIndex() == 0 ? "percent" : "prices";
                }
                return super.findImplicitPropertyName(member);
            }
        });
        return mapper;
    }

    /** Config whose getter for the binding is hidden from Jackson, so the field behind it stays out of the file's reach. */
    public static class IgnoredGetterConfig {

        private final int percent;
        private final PriceSource prices;

        @JsonCreator
        public IgnoredGetterConfig(@JsonProperty("percent") int percent, @JacksonInject PriceSource prices) {
            this.percent = percent;
            this.prices = prices;
        }

        public int getPercent() {
            return percent;
        }

        @JsonIgnore
        public PriceSource getPrices() {
            return prices;
        }
    }

    private Map<String, Configuration<Object>> read(boolean skipNonInstantiable, String className, String entry) throws IOException {
        return mapper(skipNonInstantiable).readValue(configs(className, entry));
    }

    private ConfigurationMapper<Object> mapper(boolean skipNonInstantiable) {
        return new ConfigurationMapper<>("configs", skipNonInstantiable, objectMapper, metrics, new DefaultClassFinder(), bindings);
    }

    private static String configs(String className, String entry) {
        return "{ \"configs\": { \"" + className + "\": " + entry + " } }";
    }

    private static String messages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
