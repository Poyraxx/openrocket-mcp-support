# Standalone OpenRocket MCP Repo

This repo is meant to work as a single standalone project.

## Quick start

1. Install Java 17 or newer.
2. Run `run-openrocket-live.cmd`.
3. Run `mcp\\run-openrocket-mcp.cmd`.
4. Point Codex or another MCP client at `mcp\\run-openrocket-mcp.cmd`.

## Notes

- `run-openrocket-live.cmd` launches OpenRocket with the localhost live bridge enabled.
- `mcp\\run-openrocket-mcp.cmd` builds and launches the stdio MCP server.
- External component database validation can be skipped with `-PskipExternalComponents=true`, which the launcher already uses.
