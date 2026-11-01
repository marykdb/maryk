# Maryk Website Docs

Starlight docs site for Maryk.

## Important

Part of this site is generated from repository docs before every build. Other pages are hand-authored in `src/content/docs`.

- Source of truth:
  - `core/docs/**`
  - `sql/README.md` and `sql/docs/**`
  - `store/**/README.md` and `store/**/docs/**`
  - `store/rocksdb/documentation/**`
  - `generator/README.md`
  - `cli/docs/commands.md`
  - `app/docs/README.md`
- Sync script: `scripts/sync-core-docs.mjs`
- Generated targets: `src/content/docs/**`

Edit generated pages through their source files and run `yarn sync`.
The mapping in `scripts/sync-core-docs.mjs` identifies which pages are generated.

Hand-authored pages include the landing page, introduction pages, tutorials and tradeoff guidance. Edit those directly.

## Local development

From `website/`:

Requires Node.js 22.23.4 or newer and Yarn 1.22.22.

```bash
yarn install
yarn dev
```

`yarn dev` synchronizes generated documentation before Astro starts.

Build:

```bash
yarn build
```

Preview:

```bash
yarn preview
```

## Generator checks

Run `yarn test` after changing the sync script or its mappings, then `yarn build`.
The tests cover source-relative links, generated routes and code-fence preservation.
The build synchronizes sources and checks that the resulting MDX compiles.
