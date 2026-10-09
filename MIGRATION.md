<!--
Copyright 2026 Mario Lichtsinn

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

# Migrating from Merci to Ageris

This guide covers moving from Merci 0.1.2 to Ageris 0.2.0. Configuration files need no change: the JSON structure and the `feature-flags` and `configs` root fields are the same.

## Steps

1. Run on Java 17 or later.
2. Replace the dependency.

   ```xml
   <dependency>
       <groupId>io.github.lichtsinn</groupId>
       <artifactId>ageris-core</artifactId>
       <version>0.2.0</version>
   </dependency>
   ```

   Merci's README showed its dependency with `<type>pom</type>`. Leave that line out: with it, Maven does not put the Ageris jar on the classpath.

3. Rename the package in your imports from `com.medallia.merci` to `io.github.lichtsinn.ageris`.
4. Rename the class `Merci` to `Ageris`. Its existing constructors and methods are unchanged.
5. Read the behavior changes below, the first one in particular.

The web module is not published for 0.2.0.

## Behavior changes

### A configuration that cannot be loaded now holds back the update

In Merci, an entry that could not be loaded was skipped and the rest of the update was applied. The skipped entry was no longer served, so callers got their defaults, even if an earlier version of it had loaded.

In Ageris, the manager keeps all of its entries as they were until the file is fixed. Each failed refresh is logged and counted in the loader metrics. Other managers still update.

To keep Merci's behavior, call this before building the managers:

```java
ageris.skipNonInstantiableConfiguration();
```

See [How updates are applied](README.md#how-updates-are-applied).

### Other changes

* **Newer versions of the libraries Ageris depends on.** Ageris 0.2.0 depends on Jackson 2.22, slf4j 2.0 and commons-io 2.22, where Merci 0.1.2 depended on Jackson 2.9, slf4j 1.7 and commons-io 2.6. If your application pins older versions of these, Maven or Gradle may pick yours. slf4j 2.0 needs a logging backend made for it, for example Logback 1.3 or later, or Log4j's `log4j-slf4j2-impl`.
* **Unchanged content is not loaded again.** Merci parsed the files and rebuilt every flag and config on each refresh, unless `setMaximumSkips` was called. Ageris skips a refresh whose content is the same as the content loaded last. A changed file is still picked up at the next refresh. To keep Merci's behavior, call `ageris.setMaximumSkips(0)`.
* **A `null` value is rejected when the file is loaded.** In Merci, `"value": null` loaded and then threw `NullPointerException` on every evaluation. It now counts as an entry that cannot be loaded, so the change above applies to it.
* **A fetch that returns nothing keeps the loaded configurations.** In Merci it cleared them, and callers got their defaults.
* **A failed refresh no longer ends refreshing.** In Merci, a `RuntimeException` during a refresh canceled every later refresh of that manager for the lifetime of the process.
* **Refresh intervals below one second work.** Merci truncated the interval to whole seconds. An interval below one millisecond is rejected.
* **`ConfigManager.getConfig` reports a failing no-argument constructor as `ConfigInstantiationException`.** In Merci, whatever the constructor threw escaped as is.
* **`AbstractConfigurationManager` is removed.** The three managers implement `ConfigurationManager` directly, with the same public methods.
* **Injectable values set on your own `ObjectMapper` are not used.** Ageris works on a private copy of the mapper. If a config class uses `@JacksonInject`, register the object with `ageris.bind(...)` instead, and see [Policies](README.md#policies) for the rules such a class must follow.
