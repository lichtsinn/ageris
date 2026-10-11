<!--
Copyright 2018 Medallia, Inc.
Modifications copyright 2026 Mario Lichtsinn; this file differs from the version
released by Medallia, Inc. See NOTICE.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

Ageris is a framework for feature flags, runtime configuration and policies. It relies on an easy to learn, recursive JSON structure.

Ageris continues Merci, originally developed at Medallia, Inc. and released under the Apache License 2.0.
It is not affiliated with or endorsed by Medallia. See [NOTICE](NOTICE).

Coming from Merci? See [Migrating from Merci to Ageris](MIGRATION.md).

## Features

Ageris runs inside your application. It needs no server: it reads [configuration files](#central-configuration-files) that you keep wherever you like, and refreshes them in the background.

* **[Feature flags](#toggling-features-with-ageris)** that turn code paths on and off without a deployment.
* **[Runtime configuration](#using-ageris-for-runtime-configuration)** as typed Java objects, called configs, or as plain JSON.
* **[Policies](#policies)**: configs that have behavior and work with live objects of your application.
* **[Immutable by design](#immutable-by-design).** Write a config as an immutable class or a Java record, and it stays that way: Ageris never changes it after loading. When a file changes, Ageris creates new objects and replaces the old ones, so your code never sees a half-updated config.
* **Values that depend on context.** Any feature flag, config or policy can differ by environment, cluster, user or any other context you define.
* **[Targeting](#targeting)** by a list of values and by a percentage of callers, for feature flags, configs and policies alike.
* **[Safe updates](#how-updates-are-applied).** A file that cannot be loaded changes nothing: what was loaded before keeps being served.
* **[A wait for the first load, and a status](#waiting-for-the-first-load-and-checking-status)** that tells whether a manager has loaded or is serving an older version, and why.
* **[Listeners](#listening-for-updates)** that are called when content or status changes.
* **[Reasons](#finding-out-why).** A lookup can say why it returned its value, and which context matched.
* **[OpenFeature](#using-ageris-through-openfeature).** Feature flags and JSON configs can be read through the OpenFeature API, with the `ageris-openfeature` module.
* **[Spring Boot](#using-ageris-with-spring-boot).** A starter sets Ageris up from a few properties, with the managers as beans.
* **[Helidon](#using-ageris-with-helidon).** A module sets Ageris up from the configuration of a Helidon SE application, with the managers created in one call or injected.
* **[Test support](#testing-code-that-uses-ageris)** that sets feature flags, configs and policies from Java, with no files.

## Quick Start

This guide describes with examples how to use Ageris.

### Using Ageris in your Java<sup>(TM)</sup> application

Ageris releases can be downloaded from the Maven central repository. Adding Ageris to a Java<sup>(TM)</sup> application just requires adding Ageris as a dependency. With Maven:

```xml
<dependency>
    <groupId>io.github.lichtsinn</groupId>
    <artifactId>ageris-core</artifactId>
    <version>0.5.0</version>
</dependency>
```

With Gradle:

```groovy
implementation 'io.github.lichtsinn:ageris-core:0.5.0'
```

Ageris 0.5.0 requires Java 17 or later.

### Central Configuration Files

The core idea behind Ageris is to use a small set of central, easy to read files with a recursive JSON or YAML structure. These files are fetched by Ageris's configuration loader through a scheduled, asynchronous task.

The following example of a configuration file contains a feature flag called "enable-international-welcome". Its evaluation at runtime results in a true or false value, depending on the environment of the deployed application and the current user. The feature flag would only be true, which means active, if the current user is 'joe' and the environment of the deployed application instance is 'qa'.

```JSON
{
  "feature-flags": {
    "enable-international-welcome": {
      "value": false,
      "modifiers": {
        "type": "environment",
        "contexts": {
          "qa": {
            "value": false,
            "modifiers": {
              "type": "user",
              "contexts": {
                "joe": {
                  "value": true
                }
              }
            }
          },
          "production": {
            "value": false
          }
        }
      }
    }
  }
}
```

Runtime configurations - or short configs - share the same recursive structure but instead of boolean values, their values are objects, that are deserialized to immutable config objects.

```JSON
{
  "configs": {
    "com.example.DBConfig": {
      "value": {
        "hosts": [ "invalid-host" ],
        "port": -1
      },
      "modifiers": {
        "type": "environment",
        "contexts": {
          "qa": {
            "value": {
              "hosts": [ "db1.somewhere.in.qa" ],
              "port": 9889
            }
          },
          "production": {
            "value": {
              "hosts": [ "db1.somewhere.in.prod", "db2.somewhere.in.prod", "db3.somewhere.in.prod" ],
              "port": 9889
            }
          }
        }
      }
    }
  }
}
```

#### Targeting

A modifier picks a nested entry by the exact value of its context type. It can also hold `rules` for the values that have no entry of their own:

```JSON
{
  "feature-flags": {
    "enable-checkout": {
      "value": false,
      "modifiers": {
        "type": "environment",
        "contexts": {
          "qa": {
            "value": false,
            "modifiers": {
              "type": "user",
              "contexts": { "joe": { "value": true } },
              "rules": [
                { "in": ["anna", "ben"], "value": true },
                { "percent": 10, "value": true }
              ]
            }
          }
        }
      }
    }
  }
}
```

In `qa` the feature flag is on for joe, for anna and ben, and for 10 percent of the other users. Everywhere else it is off.

At each level a named entry in `contexts` wins, then the rules are tried from top to bottom, and if none matches the value of that level applies.

| Match | True when the caller's value |
|---|---|
| `"in": ["anna", "ben"]` | equals one of the listed values, exactly |
| `"percent": 10` | falls into the first 10 percent; two decimals are allowed |

* A rule holds a `value` and may hold `modifiers` of its own, like any entry. Once a named entry or a rule is chosen, the value comes from inside it.
* A rule with both `in` and `percent` needs both: the value is listed and falls into the percentage.
* `in` holds texts in quotes. `percent` is a number from 0 to 100, without quotes.
* Rules work for feature flags, configs, JSON configs and policies, so a policy can be rolled out to a share of users.
* A caller that has no value for the context type matches no rule.
* A malformed rule, such as one with a misspelled key or with the same key twice, is refused when the file is loaded. See [How updates are applied](#how-updates-are-applied).

A percentage is decided by hashing the caller's value together with the name of the feature flag or config, so nothing is stored:

* The same caller always gets the same answer, on every server and after every restart.
* Raising a percentage keeps everyone who was in. Lowering it removes those who were added last.
* Within one feature flag or config the same callers fall into a percentage at every level, so 10 percent inside 10 percent of the same context type is still those 10 percent.
* Each feature flag or config has its own callers. Renaming it, which for a config means renaming or moving its class, changes who they are.
* `percent` means the first so many percent, so a later rule only sees callers no earlier rule matched:

```JSON
"rules": [
  { "percent": 10, "value": "A" },
  { "percent": 30, "value": "B" }
]
```

The first 10 percent get A, the next 20 percent get B, and everyone else gets the value of the level.

Upgrade every application that reads a file before using `rules` in it. An application on Ageris 0.2.0 does not know the key and cannot load the entry. It keeps the configuration it loaded before or, if it was told to skip entries it cannot load, stops serving that entry. If it was given a JSON mapper that ignores unknown properties, it loads the entry without a failure and leaves the rules out.

### Initializing Ageris
 
Ageris's configuration loader, which is responsible for scheduling retrieval and processing of configuration changes, relies on a registered configuration fetcher to retrieve the latest configuration content from a local or remote source. The library provides a generic interface, that applications implement for fetching their configuration files. For testing purposes and for applications, which only read configurations from the local file system, Ageris's Filesystem Configuration Fetcher class should be sufficient.

```Java
/**
 * Configuration fetcher for this application.
 */
public class MyAppConfigurationFetcher implements ConfigurationFetcher {
    @Override
    public Map<String, String> fetch(List<String> fileNames, String application) throws IOException {
        /* return new map with requested file names and latest configuration content for the provided application name. */
    }
}
```

Initializing Ageris's components should follow the example below. Both configuration manager singletons, the feature flag manager and the config manager instances should be made available to the rest of the application code.

```Java
/* Initialize configuration fetcher. */
ConfigurationFetcher fetcher = new MyAppConfigurationFetcher(...);

/* Use Ageris to initialize configuration managers and loader. */
Ageris ageris = new Ageris(fetcher);

FeatureFlagManager featureFlagManager = ageris.addFeatureFlagManager("myapp").registerFile("/featureflags.json").build();
ConfigManager configManager = ageris.addConfigManager("myapp").registerFile("/configs.json").build();

ConfigurationLoader loader = ageris.createLoader(Duration.ofSeconds(180));
loader.start();
```

See [How Ageris loads and updates](#how-ageris-loads-and-updates) for what happens on each refresh, how to wait for the first load, and how to listen for changes.

### Toggling Features with Ageris

Ageris's feature flag manager allows developers to selectively enable and disable parts of their code without redeploying or restarting application instances. In the following code example, the execution path is determined by applying the runtime configuration context to the external definition of the "enable-international-welcome" feature flag.

```Java
public class HelloWorld {

    private final FeatureFlagManager featureFlagManager;

    public HelloWorld(FeatureFlagManager featureFlagManager) {
        this.featureFlagManager = featureFlagManager;
    }

    @GET
    public String get(@Context HttpServletRequest request) {
        ConfigurationContext context = ...;
        /* Evaluate, if feature flag is active based on provided runtime context. */
        if (featureFlagManager.isActive("enable-international-welcome", context)) {
            return new Translator().translate("Hello World!", request.getLocale().getLanguage());
        }
        return "Hello World!";
    }
```

#### Finding out why

`isActive` returns only the answer. `evaluate` returns the answer together with the reason:

```Java
Evaluation<Boolean> evaluation = featureFlagManager.evaluate("enable-international-welcome", context);
evaluation.getValue();     // false
evaluation.getReason();    // MATCH
evaluation.getMatches();   // {environment=qa}
```

* `NOT_FOUND`: no feature flag with that name is defined. The value is `false`, or the default the caller passed.
* `STATIC`: the feature flag has no modifiers, so it has the same value for every context.
* `DEFAULT`: the feature flag has modifiers, and none matched the context. The value is its base value.
* `MATCH`: at least one modifier matched. `getMatches()` lists the context types and values that matched, outermost first.

The config manager and the JSON config manager have `evaluate` as well, next to `getConfig`.

### Using Ageris for Runtime Configuration

Runtime configs are managed by a registered instance of Ageris's config manager class. Using config objects of type HelloWorldConfig allows an external list of supported languages to be passed in at runtime.

```Java
public class HelloWorld {

    private final ConfigManager configManager;

    public HelloWorld(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @GET
    public String get(@Context HttpServletRequest request) {
        ConfigurationContext context = ...;
        /* Get config object based on provided runtime context. */
        HelloWorldConfig config = configManager.getConfig(HelloWorldConfig.class, context);
        if (config.isSupportedLanguage(request.getLocale().getLanguage())) {
            return new Translator().translate("Hello World!", request.getLocale().getLanguage());
        }
        return "Hello World!";
    }
    
    /** Config class. */
    public static class HelloWorldConfig {

        private final Set<String> languages;

        public HelloWorldConfig() {
            this(Collections.emptyList());
        }

        @JsonCreator
        public HelloWorldConfig(@JsonProperty("languages") List<String> languages) {
            this.languages = new HashSet<>(languages);
        }

        public boolean isSupportedLanguage(String language) {
            return languages.contains(language);
        }
    }
}
```

#### Immutable by design

A config can be immutable and still change at runtime. Ageris never modifies a config after loading it. When a file changes, it creates a new object and replaces the old one.

The shortest immutable config is a Java record:

```Java
public record Limits(int maxConnections, List<String> hosts) {

    public Limits {
        hosts = List.copyOf(hosts);   // a record does not make the list it holds unmodifiable
    }
}
```

```JSON
{
  "configs": {
    "com.example.Limits": {
      "value": { "maxConnections": 10, "hosts": ["db1"] },
      "modifiers": {
        "type": "environment",
        "contexts": {
          "qa": { "value": { "maxConnections": 2, "hosts": ["qa-db1"] } }
        }
      }
    }
  }
}
```

```Java
Limits limits = configManager.getConfig(Limits.class, context);
```

A record needs no Jackson annotations: the names of its components are matched to the fields in the file. A class with final fields that are set through its constructor, like `HelloWorldConfig` above, works the same way, but needs `@JsonCreator` and `@JsonProperty` on that constructor.

What Ageris guarantees:

* It never modifies a config or policy after loading it. An update creates new objects and replaces a manager's whole set in one step.
* Code that holds a config or policy keeps a consistent one. It never sees a half-updated object.
* The same object is returned for as long as the manager's files stay the same. When one of them changes, all of that manager's objects are created anew, including those whose values did not change.
* [Policies](#policies) must take their values from the file through the constructor. Ageris refuses a policy class with a setter or a writable field.

Your part:

* Write config classes as records, or with final fields and no setters. Ageris does not refuse a plain config class that has setters.
* Copy the collections a config receives, for example with `List.copyOf`, and do not hand out ones that can be modified. Ageris does not check what a config holds.
* To work with one version throughout a request, fetch the config once and keep it. Two separate lookups can return different versions if a file changes between them.
* A record has no no-argument constructor, so `getConfig` throws `ConfigInstantiationException` if the file has no entry for it.
* JSON configs are Jackson nodes, which can be modified. Treat them as read-only.
* Feature flags and configs are replaced separately. Right after a file changes, a feature flag can already be new while a config is still the previous one; see [How updates are applied](#how-updates-are-applied).

### Policies

A policy is a config that decides something. Like any config it takes its values from the configuration file, so they can differ by environment, user or any other context and change at runtime. Unlike a plain config it also has methods, which work with live objects of your application.

Those objects are its bindings. Bind them on `Ageris`:

```Java
Ageris ageris = new Ageris(fetcher);
ageris.bind(PricingService.class, pricingService);
ageris.bind("backupPricing", backupPricingService);   // by name, for a second object of the same type
```

A policy is an ordinary config class. It asks for a binding with Jackson's `@JacksonInject` on a constructor parameter; parameters with `@JsonProperty` come from the configuration file as usual.

```Java
public class DiscountPolicy {

    private final int percent;
    private final PricingService pricing;

    @JsonCreator
    public DiscountPolicy(@JsonProperty(value = "percent", required = true) int percent,
                          @JacksonInject PricingService pricing) {
        this.percent = percent;
        this.pricing = pricing;
    }

    public Money discountFor(Order order) {
        return pricing.basePrice(order).percent(percent);
    }
}
```

Policies have no section or manager of their own. They are defined in the `configs` section, with the same structure as any config, and read through the config manager:

```JSON
{
  "configs": {
    "com.example.DiscountPolicy": {
      "value": { "percent": 5 },
      "modifiers": {
        "type": "environment",
        "contexts": {
          "qa": { "value": { "percent": 50 } }
        }
      }
    }
  }
}
```

```Java
Money discount = configManager.getConfig(DiscountPolicy.class, context).discountFor(order);
```

Things to know:

* A configuration file can never supply or replace a binding. A policy takes its values from the file through constructor parameters only: if it gives a binding a `@JsonProperty` name, or leaves a field, a setter or a collection getter writable from the file, it is not loaded.
* A binding can be any object: a service, a plain value, a supplier or a factory. Use a supplier or factory when a policy needs something new on each call.
* Bindings are shared by all policies and all threads, so they must be thread-safe.
* Bind everything before the loader is started. Binding the same type or name twice is an error.
* Bindings replace any values set with `setInjectableValues` on the `ObjectMapper` you pass to Ageris; bind those objects on `Ageris` instead.
* A policy usually has no no-argument constructor, so `getConfig` throws `ConfigInstantiationException` if the file has no entry for it.
* A getter for a binding makes the field behind it writable from the file, and would also be serialized. Put `@JsonIgnore` on such a getter, or give the method another name such as `pricing()`.

### Using Ageris through OpenFeature

[OpenFeature](https://openfeature.dev) is a vendor-neutral API for feature flags. With the `ageris-openfeature` module an application can read Ageris feature flags and JSON configs through it.

```xml
<dependency>
    <groupId>io.github.lichtsinn</groupId>
    <artifactId>ageris-openfeature</artifactId>
    <version>0.5.0</version>
</dependency>
```

The module brings `ageris-core` with it. It does not bring the OpenFeature SDK: the application adds `dev.openfeature:sdk` itself, in version 1.16.0 or later.

Set up Ageris as in [Initializing Ageris](#initializing-ageris), with `createLoader` instead of `createAndStartLoader`, and hand the two managers and the loader to the provider:

```Java
FeatureFlagManager featureFlagManager = ageris.addFeatureFlagManager("myapp").registerFile("/featureflags.json").build();
JsonConfigManager jsonConfigManager = ageris.addJsonConfigManager("myapp").registerFile("/configs.json").build();
ConfigurationLoader loader = ageris.createLoader(Duration.ofSeconds(10));

OpenFeatureAPI.getInstance().setProviderAndWait(new AgerisProvider(featureFlagManager, jsonConfigManager, loader));

Client client = OpenFeatureAPI.getInstance().getClient();
boolean active = client.getBooleanValue("enable-international-welcome", false, new ImmutableContext("joe", Map.of("environment", new Value("qa"))));
```

The provider starts the loader, waits for the first load, and shuts the loader down when OpenFeature shuts the provider down. If the first load fails, `setProviderAndWait` throws with the reason. The loader keeps trying, and once both managers have loaded, the provider reports that it is ready. A provider that was shut down cannot be used again; create a new one.

**Lookups**

| Lookup | Answered from | The value in the file must be |
|---|---|---|
| Boolean | `feature-flags` | true or false |
| Text | JSON configs | a text: `"greeting": { "value": "hello" }` |
| Whole number | JSON configs | a whole number that fits an `int`: `"max-items": { "value": 10 }` |
| Decimal number | JSON configs | any number: `"ratio": { "value": 0.25 }` |
| Object | JSON configs | anything; an object becomes a structure, a list a list |

Modifiers, contexts and [rules](#targeting) work as always, so a text or a number can be targeted like a feature flag.

**Reasons and errors**

| In Ageris | OpenFeature reports |
|---|---|
| The feature flag or config has no modifiers | reason `STATIC` |
| It has modifiers, and none matched | reason `DEFAULT` |
| A named entry or a rule matched | reason `TARGETING_MATCH` |
| Nothing with that name is defined | the caller's default, error `FLAG_NOT_FOUND` |
| The value is of another kind than asked for | the caller's default, error `TYPE_MISMATCH` |

**Context**

* The targeting key becomes the context type `user`. To use another type: `AgerisProvider.builder(featureFlagManager, jsonConfigManager).loader(loader).targetingKeyType("customer").build()`.
* An attribute keeps its name. A text is passed as it is, and a number or a boolean as its text, so `tier = 3` matches `"3"` in a file. A whole number has no decimal point, also when it was passed as `3.0`.
* Dates, lists and structures are left out.
* An attribute named like the targeting key type wins over the targeting key.

**Events**

* When a manager loads new content, the provider sends "configuration changed" with the names of that manager's entries.
* When a refresh fails, the provider sends "stale" once, with the reason, and keeps answering from what was loaded before. It sends "ready" when both managers have loaded again.
* A manager that is already stale when the provider is registered, which can only happen without a loader, is not reported.

**Things to know**

* Configs of your own classes, and policies, are not available through OpenFeature, which cannot return an object of an application's class. Read them from the config manager as before; the same `Ageris` can serve both.
* A boolean lookup only sees `feature-flags`. A boolean stored as a JSON config is not found by it.
* A whole number too large for an `int` is returned as a decimal number.
* In tests, pass the managers of `InMemoryAgeris` and no loader: `new AgerisProvider(inMemory.featureFlagManager(), inMemory.jsonConfigManager())`.
* An application that starts and stops the loader itself also passes no loader.

### Using Ageris with Spring Boot

With the `ageris-spring-boot-starter` module a Spring Boot application gets Ageris from a few properties, without setup code. It needs Spring Boot 4.0 or later.

```xml
<dependency>
    <groupId>io.github.lichtsinn</groupId>
    <artifactId>ageris-spring-boot-starter</artifactId>
    <version>0.5.0</version>
</dependency>
```

```properties
ageris.application=myapp
ageris.path=/etc/myapp/config
ageris.feature-flags.files=/featureflags.json
ageris.configs.files=/configs.json
```

With these, the files are read from `/etc/myapp/config/myapp/`, and the managers are beans. A file name starts with a slash.

```Java
@RestController
class HelloWorld {

    private final FeatureFlagManager featureFlagManager;

    HelloWorld(FeatureFlagManager featureFlagManager) {
        this.featureFlagManager = featureFlagManager;
    }
}
```

The loader is started with the application and stopped with it. A manager is loaded before any bean receives it, so a bean can use it right away, even in its constructor. This also holds with `spring.main.lazy-initialization=true`.

**Properties**

| Property | Meaning | Default |
|---|---|---|
| `ageris.application` | Name of the application, as the fetcher uses it. Required. | |
| `ageris.path` | Directory that holds the configuration files. Required unless the application defines a `ConfigurationFetcher` bean. | |
| `ageris.feature-flags.files` | Files of the feature flag manager | |
| `ageris.configs.files` | Files of the config manager, for configs and policies | |
| `ageris.json-configs.files` | Files of the JSON config manager | |
| `ageris.refresh-interval` | Time between two refreshes. A number without a unit is in seconds. | `10s` |
| `ageris.first-load-timeout` | How long the start waits for the first attempt to load the files | `30s` |
| `ageris.fail-on-first-load-failure` | Whether the application fails to start when the first attempt fails or takes too long | `true` |

* A manager only exists if its files are set. Without any files set, the starter creates nothing, so adding the dependency alone changes no application.
* If the first attempt to load the files fails, the application does not start, and the error says why: a file that is not there, or the file, the entry and the problem in it. It does not wait for the timeout in that case. With `ageris.fail-on-first-load-failure=false` it starts anyway: lookups return defaults, and the loader keeps trying.

**Stepping in**

* For the bindings of [policies](#policies), and other options such as `skipNonInstantiableConfiguration()`, define an `AgerisCustomizer` bean. It is called before the managers are built:

```Java
@Bean
AgerisCustomizer bindings(PricingService pricingService) {
    return ageris -> ageris.bind(PricingService.class, pricingService);
}
```

* To read the files from somewhere other than a directory, define a `ConfigurationFetcher` bean. `ageris.path` is then not needed.
* For everything else, such as YAML files, define your own `Ageris` bean. The starter builds the managers and the loader on it.
* A manager bean of your own, such as one from `InMemoryAgeris` in a test, is used instead of the starter's.

**Health**

If the application has the health support of Spring Boot, as with Actuator, its health shows Ageris:

| Managers | Health |
|---|---|
| All loaded | `UP` |
| One or more stale | `UP`, with the reason in the details |
| One or more never loaded | `DOWN`, with the reason in the details |

A stale manager leaves the application up, because it still serves what it loaded before. Switch the indicator off with `management.health.ageris.enabled=false`.

**OpenFeature**

If the application also has `ageris-openfeature` and the OpenFeature SDK, and both feature flag files and JSON config files are set, the starter offers an `AgerisProvider` bean. Registering it is left to the application:

```Java
OpenFeatureAPI.getInstance().setProvider(agerisProvider);
```

`ageris.openfeature.targeting-key-type` sets the context type for the targeting key; see [Using Ageris through OpenFeature](#using-ageris-through-openfeature).

**Things to know**

* Spring Boot 4 uses Jackson 3 for its own JSON, and Ageris uses Jackson 2. The two exist side by side. The Jackson annotations on your config classes are the same for both, and Ageris does not use or change Spring's JSON mapper.
* On Spring Boot 3, set Ageris up in code as in [Initializing Ageris](#initializing-ageris).

### Using Ageris with Helidon

With the `ageris-helidon` module a Helidon SE application gets Ageris from Helidon's configuration. It needs Helidon 27, and with it Java 27.

```xml
<dependency>
    <groupId>io.github.lichtsinn</groupId>
    <artifactId>ageris-helidon</artifactId>
    <version>0.6.0</version>
</dependency>
```

```yaml
ageris:
  application: myapp
  path: /etc/myapp/config
  feature-flags:
    files: ["/featureflags.json"]
  configs:
    files: ["/configs.json"]
```

With these, the files are read from `/etc/myapp/config/myapp/`. A file name starts with a slash.

**Creating Ageris yourself**

```Java
AgerisHelidon ageris = AgerisHelidon.create(config.get("ageris"));

FeatureFlagManager featureFlagManager = ageris.featureFlagManager();
ConfigManager configManager = ageris.configManager();
...
ageris.close();
```

`create` starts the loader and returns when the managers are loaded. `close` stops the loader.

**Having the managers injected**

An application that uses Helidon's service registry needs no setup code. The managers can be injected:

```Java
@Service.Singleton
class Greeter {

    private final FeatureFlagManager featureFlagManager;

    @Service.Inject
    Greeter(FeatureFlagManager featureFlagManager) {
        this.featureFlagManager = featureFlagManager;
    }
}
```

Ageris is set up when the service registry starts, and stopped when it shuts down.

**Settings**

| Setting | Meaning | Default |
|---|---|---|
| `ageris.application` | Name of the application, as the fetcher uses it. Required. | |
| `ageris.path` | Directory that holds the configuration files. Required unless the application supplies a `ConfigurationFetcher`. | |
| `ageris.feature-flags.files` | Files of the feature flag manager | |
| `ageris.configs.files` | Files of the config manager, for configs and policies | |
| `ageris.json-configs.files` | Files of the JSON config manager | |
| `ageris.refresh-interval` | Time between two refreshes, written as `PT10S` | 10 seconds |
| `ageris.first-load-timeout` | How long the start waits for the first attempt to load the files, written as `PT30S` | 30 seconds |
| `ageris.fail-on-first-load-failure` | Whether the start fails when the first attempt fails or takes too long | `true` |

* A manager only exists if its files are set. Asking for one without files fails with a message that names the setting.
* With the service registry, nothing is set up while no files are set, so adding the dependency alone changes no application.
* If the first attempt to load the files fails, the application does not start, and the error says why: a file that is not there, or the file, the entry and the problem in it. With `ageris.fail-on-first-load-failure: false` it starts anyway: lookups return defaults, and the loader keeps trying.

**Stepping in**

For the bindings of [policies](#policies) and other options, use a customizer. It is called before the managers are built.

```Java
AgerisHelidon ageris = AgerisHelidon.builder()
        .config(config.get("ageris"))
        .customize(core -> core.bind(PricingService.class, pricingService))
        .build();
```

With the service registry, offer a service of type `AgerisCustomizer` instead. In the same way a `ConfigurationFetcher` of your own replaces the directory, and an `Ageris` of your own serves for everything else, such as YAML files: hand them to the builder, or offer them as services. A manager that your application offers as a service is used instead of the one of this module.

**Health**

If the application has Helidon's health library, a readiness check named `ageris` reports the managers:

| Managers | Health |
|---|---|
| All loaded | up |
| One or more stale | up, with the reason in the details |
| One or more never loaded | down, with the reason in the details |

A stale manager leaves the application up, because it still serves what it loaded before. With the service registry Helidon finds the check by itself; otherwise add `ageris.healthCheck()` to your health observer.

**OpenFeature**

If the application also has `ageris-openfeature` and the OpenFeature SDK, and both feature flag files and JSON config files are set, an `AgerisProvider` can be injected, or taken from `ageris.openFeatureProvider()`. Registering it is left to the application:

```Java
OpenFeatureAPI.getInstance().setProvider(agerisProvider);
```

`ageris.openfeature.targeting-key-type` sets the context type for the targeting key; see [Using Ageris through OpenFeature](#using-ageris-through-openfeature).

**Things to know**

* This module is for Helidon SE. Helidon MP and Helidon 4 are not covered; there, set Ageris up in code as in [Initializing Ageris](#initializing-ageris).
* It is the only module of Ageris that needs Java 27. The others need Java 17.

### Testing code that uses Ageris

`InMemoryAgeris` gives a test the same managers an application uses, with values set in Java. It needs no files, fetcher or loader.

```Java
InMemoryAgeris ageris = new InMemoryAgeris()
        .setFlag("enable-international-welcome", true)
        .setConfig(new HelloWorldConfig(Arrays.asList("de", "fr")))
        .setConfig(new DiscountPolicy(25, fakePricingService));

HelloWorld helloWorld = new HelloWorld(ageris.featureFlagManager());

/* Change a value in the middle of a test. */
ageris.setFlag("enable-international-welcome", false);
```

* A value applies to every configuration context.
* A change takes effect at once, and the manager's listeners are called.
* A policy is constructed by the test, with the objects it would be bound to or with stand-ins for them. No bindings are registered.
* A flag or config that is not set behaves as in production: the flag is off, and the config comes from its no-argument constructor.
* If the object's class is not the class your code asks for, as with a subclass or a mock, name the class: `setConfig(HelloWorldConfig.class, mockConfig)`.

### How Ageris loads and updates

The loader created in [Initializing Ageris](#initializing-ageris) fetches the configuration files on a schedule. This section describes what happens on each refresh, and how an application can follow it.

#### How updates are applied

Each manager refreshes on its own. Its files are fetched and its entries are replaced independently of the other managers, even when two managers read the same file.

* Within one manager, an update replaces all of its entries at once.
* Content that is the same as the content loaded last is not loaded again, so the objects a manager serves stay the same until a file changes. Call `ageris.setMaximumSkips(n)` to load unchanged content again after `n` refreshes, or with `0` on every refresh.
* Managers that read the same file can be on different versions of it for a moment, and for longer if one of them fails to update.
* If nothing can be fetched, or a file is not valid JSON or lacks the manager's root field, that manager keeps what it has.
* If a single entry cannot be loaded, that manager keeps all of its entries as they were, until the file is fixed. Other managers still update.
* Call `ageris.skipNonInstantiableConfiguration()` before building the managers to skip such an entry and update the rest instead. The skipped entry is then no longer served, so callers get their defaults, even if an earlier version of it had loaded. It is not tried again until a file changes.

Failed updates are logged, counted in the loader metrics, and shown in the manager's status.

#### Waiting for the first load and checking status

The loader starts in the background, so lookups made before the first load return defaults. To wait for it:

```Java
ConfigurationLoader loader = ageris.createAndStartLoader(Duration.ofSeconds(180));
if (!loader.awaitFirstLoad(Duration.ofSeconds(10))) {
    /* Not every manager has loaded. Exit, or continue on defaults. */
}
```

The wait ends once every manager has loaded and its listeners have been called for that load.

Each manager reports its status:

```Java
ConfigurationStatus status = configManager.getStatus();
if (status.getState() == ConfigurationStatus.State.STALE) {
    log.warn("Configs are stale", status.getFailure());
}
```

* `NOT_LOADED`: no load has succeeded, and lookups return defaults.
* `LOADED`: the last refresh succeeded.
* `STALE`: the last refresh failed, and the manager serves what it loaded before.

`getFailure()` returns the exception of the last failed refresh, or `null`.

#### Listening for updates

A manager calls its listeners after it has loaded new content:

```Java
configManager.addListener(update ->
        pool.resize(configManager.getConfig(PoolConfig.class, context).getSize()));
```

* A listener is called on the first load and whenever the content of the manager's files changes. It is not called for unchanged content or for a refresh that failed.
* It is called after the entries were replaced, so lookups made inside it return the new values.
* Keep a listener quick. It runs on the loader's thread, and the manager's next refresh starts only after it has returned: a refresh is never skipped, but a slow listener delays it, and with it the moment a changed file is picked up. If you pass Ageris an executor with a single thread, the refreshes of the other managers wait as well. Hand slow work to an executor of your own.
* An exception thrown by a listener is logged. The update stays applied and the other listeners are still called.

A listener that is a class can also hear when the status of the manager changes:

```Java
configManager.addListener(new ConfigurationListener() {
    @Override
    public void onUpdate(ConfigurationUpdate update) { }

    @Override
    public void onStatusChange(ConfigurationStatus status) {
        if (status.getState() == ConfigurationStatus.State.STALE) {
            alerts.raise("Configs are stale", status.getFailure());
        }
    }
});
```

`onStatusChange` is called when a manager has loaded for the first time, when a refresh fails and leaves it stale, when it recovers, and when its first load fails. It is not called again while the manager stays stale.
