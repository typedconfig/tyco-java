# tyco-java

Java implementation of the Tyco configuration language parser. This port mirrors the Python reference implementation and is kept in sync with the canonical spec (`tyco-website/v0.1.0.html`) and shared test suite (`tyco-test-suite`).

## Features

- Full support for Tyco's type system: `str`, `int`, `float`, `decimal`, `bool`, `date`, `time`, `datetime`, nullable fields, and arrays
- Struct definitions with primary keys, defaults, references, and template rendering
- Lexer/AST pipeline copied from the Python/JS implementations for spec fidelity
- `TycoParser.load(path)` and `TycoParser.loads(content)` entrypoints that return JSON-like `Map<String,Object>` results
- Shared test harness that replays every fixture in `../tyco-test-suite/inputs`

## Requirements

- Java 11+
- Maven 3.9+
- The `tyco-test-suite` checkout at `../tyco-test-suite` (run `./setup-test-suite.sh` from the workspace root to fetch/update it)

## Building

```bash
cd tyco-java
mvn -Dmaven.repo.local=/path/to/.m2 package
```

The custom repo path is optional but convenient inside the workspace where the default home directory may be read-only.

## Testing

`mvn test` executes `TycoParserTest`, which:

1. Walks every `.tyco` file under `../tyco-test-suite/inputs`
2. Skips helper fixtures that do not have a corresponding JSON expectation
3. Parses with `TycoParser.load` and compares the result to the canonical JSON in `../tyco-test-suite/expected`

```bash
cd tyco-java
mvn -Dmaven.repo.local=/path/to/.m2 test
```

## Usage

The repository bundles the canonical configuration sample under `tyco/example.tyco`
([view on GitHub](https://github.com/typedconfig/tyco-java/blob/main/tyco/example.tyco)).

```java
import io.typedconfig.tyco.TycoParser;

Map<String, Object> config = TycoParser.load("tyco/example.tyco");

String timezone = (String) config.get("timezone");
System.out.println("timezone=" + timezone);

@SuppressWarnings("unchecked")
List<Map<String, Object>> applications = (List<Map<String, Object>>) config.get("Application");
Map<String, Object> primaryApp = applications.get(0);
System.out.println("primary service -> " + primaryApp.get("service") + \" (\" + primaryApp.get("command") + \")\");

@SuppressWarnings("unchecked")
List<Map<String, Object>> hosts = (List<Map<String, Object>>) config.get("Host");
Map<String, Object> backupHost = hosts.get(1);
System.out.println("host " + backupHost.get("hostname") + \" cores=\" + backupHost.get("cores"));
```

### Example Tyco File

```
tyco/example.tyco
```

```tyco
str timezone: UTC  # this is a global config setting

Application:       # schema defined first, followed by instance creation
  str service:
  str profile:
  str command: start_app {service}.{profile} -p {port.number}
  Host host:
  Port port: Port(http_web)  # reference to Port instance defined below
  - service: webserver, profile: primary, host: Host(prod-01-us)
  - service: webserver, profile: backup,  host: Host(prod-02-us)
  - service: database,  profile: mysql,   host: Host(prod-02-us), port: Port(http_mysql)

Host:
 *str hostname:  # star character (*) used as reference primary key
  int cores:
  bool hyperthreaded: true
  str os: Debian
  - prod-01-us, cores: 64, hyperthreaded: false
  - prod-02-us, cores: 32, os: Fedora

Port:
 *str name:
  int number:
  - http_web,   80  # can skip field keys when obvious
  - http_mysql, 3306
```

## Development Notes

- Run `./setup-test-suite.sh` from the workspace root whenever the shared tests change, then rerun `mvn test`.
- The lexer (`TycoLexer`) and value layer (`TycoValue`) intentionally mirror the Python implementation for easier diffing.
- JaCoCo is wired via the Maven build and will emit coverage reports under `target/site/jacoco`.
