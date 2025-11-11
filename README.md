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

```java
import io.typedconfig.tyco.TycoParser;

// Parse a Tyco configuration file
Map<String, Object> context = TycoParser.load("config.tyco");

// Access global configuration values
Map<String, Object> globals = (Map<String, Object>) context.get("globals");
String environment = (String) globals.get("environment");
Boolean debug = (Boolean) globals.get("debug");
Double timeout = (Double) globals.get("timeout");

// Get all instances as lists
Map<String, Object> objects = (Map<String, Object>) context.get("objects");
List<Map<String, Object>> databases = (List<Map<String, Object>>) objects.get("Database");
List<Map<String, Object>> servers = (List<Map<String, Object>>) objects.get("Server");

// Access individual instance fields
Map<String, Object> primaryDb = databases.get(0);
String dbHost = (String) primaryDb.get("host");
Integer dbPort = (Integer) primaryDb.get("port");
```

## Development Notes

- Run `./setup-test-suite.sh` from the workspace root whenever the shared tests change, then rerun `mvn test`.
- The lexer (`TycoLexer`) and value layer (`TycoValue`) intentionally mirror the Python implementation for easier diffing.
- JaCoCo is wired via the Maven build and will emit coverage reports under `target/site/jacoco`.
