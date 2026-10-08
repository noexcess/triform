# Contributing to Triform

Thanks for your interest! Triform is a minimal RPC & deployment base: one codebase, three run forms.

## How to contribute

1. Fork & create a branch from `main`.
2. Keep the core minimal — anything not on the `triform-rpc` dependency chain belongs to the host project, not here (see `docs/PROVENANCE.md` and design doc 44).
3. Run the full build before pushing:

```bash
mvn -s .mvn-settings.xml install
```

This runs unit tests + build-time wiring guards (`triform-testing`). Guards that fail are bugs — fix the wiring, don't disable the guard.

4. If your change affects the three-form contract (mono / micro-dubbo / micro-http), verify at least two forms end-to-end with the sample (`all-in-one` + `service/*`) and describe how in your PR.

## Rules of thumb

- Zero transport annotations on business contracts. `@RemoteFacade` is the only marker.
- Explicit over implicit: new auto-configuration must be documented in README's configuration table.
- Every added dependency must be Apache-2.0 compatible and justified in the PR.

## License

By contributing you agree that your contributions are licensed under Apache-2.0.
