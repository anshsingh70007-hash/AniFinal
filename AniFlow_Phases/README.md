# AniFlow — Phase Playbooks

This folder is the execution plan for the AniFlow rebuild, written to be handed to another AI agent
that will do the work **one phase per session**. It is not a design essay. Every file here is a
work order.

## Read in this order

1. `../CLAUDE.md` — project rules, locked decisions, build commands. Non-negotiable.
2. `../AniFlow_Phase0_Audit.md` — 21 sections, the source of truth for what is wrong with the app.
   **Do not re-derive it.** Sections 13–20 hold the product vision, target package tree, navigation
   design, mobile/TV strategy and the roadmap.
3. `REFERENCE_CODEBASE_MAP.md` — hard facts about the current tree (file list, LOC, real API
   signatures, verified grep counts, and the environment bugs that will otherwise cost you an hour).
4. `P0_STATUS_AND_FINISH.md` — what has already been changed on disk. Read this before touching
   anything, or you will re-do or undo finished work.
5. The phase file you were asked to execute. Only that one.

## Phase status

| Phase | File | Status |
|---|---|---|
| P0 — correctness, security, size | `P0_STATUS_AND_FINISH.md` | **Code complete, compiles, unit tests pass.** Lint + release APK + on-device validation outstanding |
| P1 — design system & result types | `P1_DESIGN_SYSTEM.md` | Not started |
| P2 — flavour collapse & new IA | `P2_FLAVOUR_COLLAPSE_AND_IA.md` | Not started |
| P3 — Room, offline-first | `P3_ROOM_OFFLINE_FIRST.md` | Not started |
| P4 — TV as a first-class product | `P4_TV_FIRST_CLASS.md` | Not started |
| P5 — player | `P5_PLAYER.md` | Not started |
| P6 — growth | `P6_GROWTH.md` | Not started |

Phases are ordered by risk retired per unit of work and they have real dependencies: P1 introduces
the tokens P2 lays out with, P2 deletes the second UI tree that would otherwise double P4's work,
P3 gives P4/P5 the offline read path and cross-episode resume they assume. **Do not reorder.**

## How to work (this is how the previous agent worked — mimic it)

- **One phase per session.** Finish it, build it, report it, stop. Never start the next phase
  because you have context left.
- **Evidence or silence.** Every claim that something is broken needs `file:line`. Keep *confirmed*
  facts separate from *suspected* ones. Never present an assumption as a fact. If you did not read
  it or run it, say so.
- **Grep every call site before you rename or delete a symbol.** Not the file — the symbol. The
  previous agent deleted 1,222 LOC of dead screens only after grepping all 13 declared top-level
  symbols individually and getting zero external hits.
- **Build after every phase, and do not report a phase as done until it compiles.** "I read the
  change and it looks right" is not verification. Say what you statically checked and what still
  needs a compile or a device.
- **Bias toward deleting code.** The owner has explicitly authorised it. Do not preserve bad
  architecture because it exists.
- **Be blunt.** If an instruction in `CLAUDE.md`, the audit, or one of these playbooks is wrong,
  say so with evidence and propose the correction. This has already happened once — see the nav3
  `onBack` correction in `P0_STATUS_AND_FINISH.md`. The owner prefers being contradicted to being
  humoured.
- **Token frugality is a hard requirement.** Don't re-read unchanged files. Don't dump whole files
  into your reply. Prefer targeted `grep`/`Grep` over reading. Delegate wide sweeps to sub-agents so
  findings, not file contents, land in context.
- **Don't over-engineer.** No new abstraction without a named problem it solves. No new library
  without a reason. No refactoring for aesthetics.
- **Long output goes in a Markdown file in the repo root, not in chat.** Chat replies stay dense
  and short.

## Build and verify (Windows, this machine)

Two environment traps on this machine, both already diagnosed. Use this exact recipe:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export TMP="C:\\gradle_tmp"
export TEMP="C:\\gradle_tmp"
./gradlew.bat assembleStandardDebug --console=plain
```

1. There is **no `java` on PATH**. The only JDK is Android Studio's JBR (OpenJDK 25) at
   `C:\Program Files\Android\Android Studio\jbr`.
2. Without `TMP`/`TEMP` pointed at a **short, space-free path**, every Gradle invocation dies with
   `java.io.IOException: Unable to establish loopback connection`. Root cause: `Selector.open()` →
   `PipeImpl$Initializer$LoopbackConnector` → `UnixDomainSockets.connect` fails because the AF_UNIX
   socket directory comes from `TMP` and resolves to the 8.3 short path
   `C:\Users\HARMEE~1\AppData\Local\Temp`. `mkdir -p /c/gradle_tmp` once, then export as above.
   Overriding `java.io.tmpdir` does **not** fix it; the AF_UNIX dir comes from `TMP` /
   `jdk.net.unixdomain.tmpdir`.

Tasks:

```bash
./gradlew.bat assembleStandardDebug     # must pass before you report a phase done
./gradlew.bat testStandardDebugUnitTest
./gradlew.bat lintStandardDebug
./gradlew.bat assembleStandardRelease   # signed, minified; see P0 doc for the keystore
```

Debug APK lands at `app/build/outputs/apk/standard/debug/app-standard-debug.apk`.

The owner (Harmeet) does all on-device / Android Studio testing himself. Your job ends at a green
build plus a written validation checklist for him to run. Do not ask him to run gradle for you.

## Git

Current branch is `1.8.6`; main branch is `main`. Do not commit unless asked. Do not push to `main`.
`keystore/` and `keystore.properties` are gitignored and must stay that way — they are real signing
material.
