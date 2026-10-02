# Standalone OpenRocket MCP Repo

This repo is meant to work as a single standalone project.

## Quick start

1. Install JDK 17 and set `JAVA_HOME`.
2. Clone with `git clone --recurse-submodules https://github.com/Poyraxx/openrocket-mcp-support.git`.
3. Run `run-openrocket-live.cmd` from the cloned directory.
4. Configure your MCP client to launch `cmd.exe /c <absolute-path-to-mcp\run-openrocket-mcp.cmd>` over STDIO. The client starts the server; a separate manual server process is not required.
5. Open a design and use `list_targets`, `attach_target`, then `get_snapshot`.

## Notes

- `run-openrocket-live.cmd` launches OpenRocket with the localhost live bridge enabled.
- `mcp\run-openrocket-mcp.cmd` builds and launches the STDIO MCP server with build messages on stderr.
- The live bridge listens only on localhost and requires the token stored in `%USERPROFILE%\.openrocket\remote-bridge.json`. Do not commit this file.
- For an existing clone, run `git submodule update --init --recursive` to restore the expanded component catalog.
- External component database validation can be skipped with `-PskipExternalComponents=true`, which the launcher already uses.
