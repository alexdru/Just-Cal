# Local code navigation with Graphify

Install the tested isolated CLI once:

```sh
uv tool install graphifyy==0.9.97
```

From this repository, use:

```sh
python3 scripts/graphify-project.py query "FoodRecognitionRouter PackageMacroAssistance EditorViewModel"
python3 scripts/graphify-project.py explain "FoodRecognitionRouter"
python3 scripts/graphify-project.py path "FoodRecognitionRouter" "PackageMacroAssistance"
python3 scripts/graphify-project.py status
python3 scripts/graphify-project.py refresh
```

The wrapper automatically rebuilds a missing or stale local index before queries.
It fingerprints source contents, including uncommitted/new/deleted files, and checks
again after extraction. A stale graph discards disposable index/cache state and triggers a full AST rescan (`--force`)
to avoid manifest shortcuts missing edits whose modification time was preserved. Each checkout/worktree keeps its own ignored graphify-out/.
Queries and refreshes through this wrapper are serialized. Concurrent source edits
can still race a query: pause edits for a consistent investigation and verify the
actual source before changes. Use status for a content check without rebuilding.

The pinned CLI performs local structural code extraction, without LLM calls or
semantic document processing. Markdown is explicitly excluded so refreshes keep
the same corpus. No Git hooks, background watcher, MCP server or cloud service is
installed. Normal rg/source navigation remains available. The graph is a map of
possible dependencies, not proof of execution, framework behavior or model quality.
Kotlin parsing may be partial; inspect warnings and check the relevant source.
Gradle scripts, generated Hilt/Room bindings and native behavior need direct checks.

The quick pilot is workload-specific: one recognition investigation used 8.3%
fewer total tokens and 36.5% less uncached input plus output, but took 32% longer.
It does not establish universal savings. Prefer this tool for cross-file questions;
for an exact symbol or a small edit, direct search can be simpler.

Generated graphs/caches are disposable and must not be committed. To remove the
integration, delete the wrapper, this guide, the Graphify AGENTS section and ignore
entries; remove the local graphify-out/ directory. The CLI can be removed separately
with `uv tool uninstall graphifyy`. Application builds do not depend on this tool.
