# Ageris examples

Two small web applications that do the same thing with Ageris, one on Helidon SE and one on Spring Boot. Each is a project of its own and uses the released Ageris from Maven Central, as your application would.

| Folder | Application | Needs |
|---|---|---|
| [`helidon`](helidon) | Helidon 27 SE, with `ageris-helidon` | Java 27 |
| [`spring-boot`](spring-boot) | Spring Boot 4, with `ageris-spring-boot-starter` | Java 17 or later |
| [`config`](config) | The configuration files both applications read | |

## What they show

| Call | Shows |
|---|---|
| `/greeting?user=anna` | A feature flag with targeting: the new greeting is on for anna and ben, and for 20 percent of everyone else |
| `/limits?plan=pro` | A config as an immutable Java record, with other values for the plan `pro` |
| The health of the application | What Ageris has loaded |

The feature flag and the config are in [`config/myapp/featureflags.json`](config/myapp/featureflags.json) and [`config/myapp/configs.json`](config/myapp/configs.json).

## Run the Helidon example

```
cd examples/helidon
../../mvnw compile exec:java
```

## Run the Spring Boot example

```
cd examples/spring-boot
../../mvnw spring-boot:run
```

Both listen on port 8080, so run one at a time.

## Try it

```
curl "http://localhost:8080/greeting?user=anna"     # Welcome aboard, anna!
curl "http://localhost:8080/greeting?user=carla"    # Hello, carla.
curl "http://localhost:8080/limits"                 # maxItems=10 regions=[eu]
curl "http://localhost:8080/limits?plan=pro"        # maxItems=100 regions=[eu, us]
```

The health of the application shows what Ageris has loaded:

```
curl "http://localhost:8080/observe/health"         # Helidon
curl "http://localhost:8080/actuator/health"        # Spring Boot
```

## Change a file while the application runs

This is what Ageris is for. Leave the application running, open `config/myapp/featureflags.json`, and change the percentage:

```json
{ "percent": 100, "value": true }
```

Save the file and ask again within a few seconds:

```
curl "http://localhost:8080/greeting?user=carla"    # Welcome aboard, carla!
```

No restart, no deployment. Set it back to 20, and carla gets the old greeting again.

A few things to notice:

* **The same user gets the same answer in both applications.** Which users fall into the 20 percent depends only on the user and on the name of the feature flag, not on the application or the server. Try `user-9` and `user-15` in both.
* **Raising the percentage keeps everyone who was in.** Go from 20 to 50, and whoever had the new greeting still has it.
* **A mistake in a file changes nothing.** Write `"percent": 150` and save: the application keeps the configuration it had, and its health shows the reason. Fix the file, and it loads again.

## Where to look in the code

* Helidon: [`Main.java`](helidon/src/main/java/io/github/lichtsinn/ageris/example/Main.java) sets Ageris up with one line, `AgerisHelidon.create(config.get("ageris"))`, from the settings in [`application.yaml`](helidon/src/main/resources/application.yaml). An application that uses Helidon's service registry can have the managers injected instead.
* Spring Boot: there is no setup code. The settings are in [`application.properties`](spring-boot/src/main/resources/application.properties), and [`ExampleController.java`](spring-boot/src/main/java/io/github/lichtsinn/ageris/example/ExampleController.java) has the managers injected.
* [`Limits.java`](helidon/src/main/java/io/github/lichtsinn/ageris/example/Limits.java) is the config: a record, with no annotations.

Both applications refresh every two seconds, so that a change shows quickly. The default is ten seconds.
