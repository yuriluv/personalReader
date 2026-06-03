# YuriReader (Librera FD Personal Fork)

Personal-only fork of Librera FD / LibreraReader for Korean web novel reading.

## Project Identity

- **Name**: YuriReader (root project name: Librera)
- **GitHub**: `yuriluv/personalReader`
- **Default branch**: `main` (renamed from test-workflow, 2026-06)
- **Working copy (OCM)**: `~/workspace/repos/personalReader`
- **Working copy (direct)**: `~/personalReader`
- **Original reference**: `~/librera-original` (clean upstream clone)
- **Target**: Personal Android phone only (arm64-v8a)

## Core Rules

- **Personal-only fork** — no Play Store, no public distribution, no analytics, no monetization, no marketing
- **CI-only build** — do NOT start local/proot builds unless user explicitly overrides
- **Minimal, localized diffs** — prefer small changes over large rewrites
- **Preserve existing reader behavior** — unless explicitly changing it
- **Inspect before editing** — understand the codebase before making changes
- **Verify before claiming completion** — CI validates; Termux adb for on-device check

## Authentication (see `ssh-key-management` skill for details)

| Method | Priority | Use case |
|--------|----------|----------|
| SSH (`git@github.com:443`) | 1st | git push/pull/fetch, branch management |
| PAT (`~/.ssh/github-pat`) | 2nd | GitHub REST API (PR, CI check) |
| ADB (android-tools) | — | APK install, logcat, on-device debug |

- SSH config: `~/.ssh/config` routes `github.com` → `ssh.github.com:443`
- PAT: Fine-grained, Contents:write OK, Administration:write N/A → repo settings via GitHub web
- ADB: wireless debugging, 1-time pairing then persistent

## Skill Routing (Automatic)

For non-trivial changes, inspect structure, plan, implement, then verify.

| Situation | Load these skills |
|-----------|------------------|
| Ambiguous feature request | `grill-with-docs` — grill before coding |
| Bug / build failure / crash | `systematic-debugging` — find root cause first |
| YuriReader-specific work | `yurireader-development` — CI, MuPDF, phases, pitfalls |
| Clear behavior change | `test-driven-development` when practical |
| Android build / Gradle / device work | `android-setup`, `gradle` |
| Test setup / regression checks | `android-testing` |
| Android UI / insets / fullscreen / Android 15 | `edge-to-edge` patterns |
| Shrinking / obfuscation / app size | `performance` |
| Compose UI work | `compose-ui`, `material3` |
| Kotlin patterns | `kotlin-patterns` |

## OMO / Ultrawork Restrictions

- OMO is **only** for already-specified bulk repetitive work
- Do NOT use OMO for: ambiguous design, architecture, storage, rendering, debugging
- Do NOT mix OMO with Superpowers in the same profile

## Build / Verify

**CI-only**: Push to GitHub → GitHub Actions builds → download artifact → Termux adb install.

```bash
# Push (SSH)
git push origin main

# After CI passes, install APK
adb install -r /path/to/yReader-arm64.apk

# Crash log
adb logcat -d -b crash | grep -E 'yuri.reader|FATAL' -A80
```

## Project Structure

```
personalReader/
├── app/               # Main app module
├── libPro/            # Pro library
├── libDepFree/        # Dependency-free library
├── libDepPro/         # Pro dependency library
├── libReflow/         # Reflow library
├── Builder/           # Build tools
├── composeApp/        # Compose UI (commented out in settings)
├── shared/            # Shared code (commented out in settings)
├── docs/              # Documentation
├── iosApp/            # iOS code — ignore
├── fastlane/          # Release automation — ignore
├── build.gradle.kts   # Root build (Kotlin DSL)
└── settings.gradle.kts
```