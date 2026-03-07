# kotlin-holons

**Kotlin SDK for Organic Programming** — transport primitives,
serve-flag parsing, identity parsing, discovery, TCP-based `connect()`,
and a Holon-RPC client.

## Test

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 gradle test -Dorg.gradle.java.home=/opt/homebrew/opt/openjdk@21
```

## API surface

| Object | Description |
|--------|-------------|
| `Transport` | `parseURI(uri)`, `listen(uri)`, `scheme(uri)` |
| `Serve` | `parseFlags(args)` |
| `Identity` | `parseHolon(path)` |
| `Discover` | `discover(root)`, `discoverLocal()`, `discoverAll()`, `findBySlug(slug)`, `findByUUID(prefix)` |
| `Connect` | `connect(target)`, `connect(target, options)`, `disconnect(channel)` |
| `HolonRPCClient` | `connect(url)`, `invoke(method, params)`, `register(method, handler)`, `close()` |

## Current scope

- Runtime transports: `tcp://`, `unix://`, `mem://`
- `stdio://`, `ws://`, and `wss://` are metadata-only at the transport layer
- Discovery scans local, `$OPBIN`, and cache roots
- `Connect.connect()` resolves direct targets or slugs and launches
  daemons on ephemeral localhost TCP

## Current gaps vs Go

- `connect()` is TCP-only today.
- There is no full `serve.run(...)` lifecycle helper yet.
- There is no Holon-RPC server module yet.
