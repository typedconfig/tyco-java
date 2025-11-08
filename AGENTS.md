# Repository Guidelines

## Project Structure & Module Organization
The root `~/typedconfig` repo carries workspace-only assets: `README.md`, setup guides, and VS Code configuration (`tyco-workspace.code-workspace`). Shared scripts live alongside (`setup-test-suite.sh`, `sync-tests.sh`) and should stay POSIX-compliant so every contributor can run them. Each language implementation (`tyco-python`, `tyco-js`, `tyco-java`, `tyco-cpp`, etc.) is a sibling git clone managed inside this workspace; edit language code within its own repo but use this workspace to coordinate tasks. The canonical tests live in `tyco-test-suite/inputs` and `tyco-test-suite/expected`, while the optional `tyco-vscode` directory houses the syntax-highlighting extension.

## Build, Test, and Development Commands
- `./setup-test-suite.sh` – bootstrap or refresh the shared test suite checkout.
- `./sync-tests.sh` – mirror `tyco-test-suite` cases into every implementation.
- `code tyco-workspace.code-workspace` – open the multi-root workspace with predefined tasks.
- `cd tyco-python && pytest -v` – run the reference implementation tests; adapt the path for other languages (`npm test`, `cargo test`, `go test -v ./...`).
- `Ctrl+Shift+P → Tasks: Run Task → Test All Implementations` – sequentially run language tasks from VS Code.

## Coding Style & Naming Conventions
Honor each implementation’s native formatter (e.g., Black for Python, Prettier for JS, clang-format for C++); never mix language styles in this repo. `.tyco` fixtures use lowercase snake_case names (`basic_types.tyco`) with matching JSON in `expected/<name>.json` formatted using two-space indentation. Scripts should stay executable (`chmod +x`) and prefer descriptive function names (e.g., `sync_tests(){ ... }`) to aid automation.

## Testing Guidelines
Treat the Python implementation as the oracle: add or modify tests there first, generate the JSON expectation, then copy both files into `tyco-test-suite` before calling `./sync-tests.sh`. Run per-language suites (`pytest`, `npm test`, `cargo test`, `go test -v ./...`) plus any platform-specific checks declared in `.vscode/tasks.json`. Do not merge unless every implementation passes locally; when adding cases, explain failures inside the PR and keep incomplete expectations out of `main`.

## Commit & Pull Request Guidelines
Commits in this repo follow short, imperative subjects (`Add publishing guide`, `Update Copilot instructions`). Keep related changes grouped (docs vs. scripts) and include scope prefixes when helpful (`sync:`, `docs:`). Pull requests should describe what changed, why the workspace needs it, and how it was tested (list the commands above, attach screenshots for VS Code UX tweaks, and link any upstream implementation issues). Mention if contributors must re-run setup scripts after merging.
