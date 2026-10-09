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
* **[Safe updates](#how-updates-are-applied).** A file that cannot be loaded changes nothing: what was loaded before keeps being served.
* **[A wait for the first load, and a status](#waiting-for-the-first-load-and-checking-status)** that tells whether a manager has loaded or is serving an older version, and why.
* **[Listeners](#listening-for-updates)** that are called when content or status changes.
* **[Reasons](#finding-out-why).** A lookup can say why it returned its value, and which context matched.
* **[Test support](#testing-code-that-uses-ageris)** that sets feature flags, configs and policies from Java, with no files.

## Quick Start

This guide describes with examples how to use Ageris.

### Using Ageris in your Java<sup>(TM)</sup> application

Ageris releases can be downloaded from the Maven central repository. Adding Ageris to a Java<sup>(TM)</sup> application just requires adding Ageris as a dependency. With Maven:

```xml
<dependency>
    <groupId>io.github.lichtsinn</groupId>
    <artifactId>ageris-core</artifactId>
    <version>0.2.0</version>
</dependency>
```

With Gradle:

```groovy
implementation 'io.github.lichtsinn:ageris-core:0.2.0'
```

Ageris 0.2.0 requires Java 17 or later.

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

A class with final fields that are set through its constructor, like `HelloWorldConfig` above, works the same way.

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
