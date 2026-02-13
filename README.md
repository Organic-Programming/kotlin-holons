---
# Cartouche v1
title: "kotlin-holons — Kotlin SDK for Organic Programming"
author:
  name: "B. ALTER"
  copyright: "© 2026 Benoit Pereira da Silva"
created: 2026-02-12
revised: 2026-02-13
lang: en-US
access:
  humans: true
  agents: false
status: draft
---
# kotlin-holons

**Kotlin SDK for Organic Programming** — transport, serve, identity,
and Holon-RPC client utilities for building holons in Kotlin.

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
| `HolonRPCClient` | `connect(url)`, `invoke(method, params)`, `register(method, handler)`, `close()` |

## Transport support

| Scheme | Support |
|--------|---------|
| `tcp://<host>:<port>` | Bound server socket (`Transport.Listener.Tcp`) |
| `unix://<path>` | Parsed; runtime binding requires Unix-domain capable gRPC stack |
| `stdio://` | Listener marker (`Transport.Listener.Stdio`) |
| `mem://` | Listener marker (`Transport.Listener.Mem`) |
| `ws://<host>:<port>` | Listener metadata (`Transport.Listener.WS`) |
| `wss://<host>:<port>` | Listener metadata (`Transport.Listener.WS`) |

## Parity Notes vs Go Reference

Implemented parity:

- URI parsing and listener dispatch semantics
- Native runtime listener for `tcp://`
- Holon-RPC client protocol support over `ws://` / `wss://`
- Standard serve flag parsing
- HOLON identity parsing

Not currently achievable in this minimal Kotlin core (justified gaps):

- `unix://` native runtime binding:
  - Requires a Unix-domain capable Kotlin/JVM gRPC runtime stack (typically Netty native transport).
  - This SDK intentionally stays on pure JVM socket primitives.
- `stdio://` and `mem://` runtime listeners:
  - gRPC Kotlin/Java does not expose official stdio/memory transports equivalent to Go `net.Listener` patterns.
- `ws://` / `wss://` runtime listener parity:
  - No official WebSocket server transport for standard gRPC HTTP/2 framing in the core stack.
  - Exposed as metadata only.
- Transport-agnostic gRPC client helpers (`Dial`, `DialStdio`, `DialMem`, `DialWebSocket`):
  - Requires a dedicated Kotlin/JVM gRPC adapter layer that is not yet included.
