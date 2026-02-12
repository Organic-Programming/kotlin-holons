---
# Cartouche v1
title: "kotlin-holons — Kotlin SDK for Organic Programming"
author:
  name: "B. ALTER"
  copyright: "© 2026 Benoit Pereira da Silva"
created: 2026-02-12
revised: 2026-02-12
lang: en-US
access:
  humans: true
  agents: false
status: draft
---
# kotlin-holons

**Kotlin SDK for Organic Programming** — transport, serve, and identity
utilities for building holons in Kotlin.

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

## Transport support

| Scheme | Support |
|--------|---------|
| `tcp://<host>:<port>` | Bound server socket (`Transport.Listener.Tcp`) |
| `unix://<path>` | Parsed; runtime binding requires Unix-domain capable gRPC stack |
| `stdio://` | Listener marker (`Transport.Listener.Stdio`) |
| `mem://` | Listener marker (`Transport.Listener.Mem`) |
| `ws://<host>:<port>` | Listener metadata (`Transport.Listener.WS`) |
| `wss://<host>:<port>` | Listener metadata (`Transport.Listener.WS`) |
