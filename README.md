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

String environment = (String) config.get("environment");
Boolean debug = (Boolean) config.get("debug");
Number timeout = (Number) config.get("timeout");

@SuppressWarnings("unchecked")
List<Map<String, Object>> databases = (List<Map<String, Object>>) config.get("Database");
Map<String, Object> primaryDb = databases.get(0);
String dbHost = (String) primaryDb.get("host");
Number dbPort = (Number) primaryDb.get("port");
```

### Example Tyco File

```
tyco/example.tyco
```

```tyco
# Global configuration with type annotations
str environment: production
bool debug: false
int timeout: 30

# Database configuration struct
Database:
 *str name:           # Primary key field (*)
  str host:
  int port:
  str connection_string:
  # Instances
  - primary, localhost,    5432, "postgresql://localhost:5432/myapp"
  - replica, replica-host, 5432, "postgresql://replica-host:5432/myapp"

# Server configuration struct  
Server:
 *str name:           # Primary key for referencing
  int port:
  str host:
  ?str description:   # Nullable field (?) - can be null
  # Server instances
  - web1,    8080, web1.example.com,    description: "Primary web server"
  - api1,    3000, api1.example.com,    description: null
  - worker1, 9000, worker1.example.com, description: "Worker number 1"

# Feature flags array
str[] features: [auth, analytics, caching]
```

## Development Notes

- Run `./setup-test-suite.sh` from the workspace root whenever the shared tests change, then rerun `mvn test`.
- The lexer (`TycoLexer`) and value layer (`TycoValue`) intentionally mirror the Python implementation for easier diffing.
- JaCoCo is wired via the Maven build and will emit coverage reports under `target/site/jacoco`.
