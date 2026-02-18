# Maryk Website Docs

Starlight docs site for Maryk.

## Important

Part of this site is generated from repository docs before every build.

- Source of truth:
  - `core/docs/**`
  - `store/**/README.md` and `store/**/docs/**`
  - `cli/docs/commands.md`
  - `app/docs/README.md`
- Sync script: `scripts/sync-core-docs.mjs`
- Generated targets: `src/content/docs/**`

Do not hand-edit generated pages unless you also update their source files.

## Local development

From `website/`:

```bash
yarn install
yarn dev
```

Build:

```bash
yarn build
```

Preview:

```bash
yarn preview
```
