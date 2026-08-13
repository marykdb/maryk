import { createReadStream, existsSync } from "node:fs";
import { createServer } from "node:http";
import { resolve, sep } from "node:path";
import { webkit } from "playwright";

const repositoryRoot = resolve(import.meta.dirname, "../../..");
const artifacts = {
  "/js/": resolve(repositoryRoot, "store/indexeddb/build/compileSync/js/test/testDevelopmentExecutable"),
  "/wasm/": resolve(repositoryRoot, "store/indexeddb/build/compileSync/wasmJs/main/developmentLibrary/kotlin"),
  "/wasm-node-modules/": resolve(repositoryRoot, "build/wasm/node_modules"),
};

for (const artifact of Object.values(artifacts)) {
  if (!existsSync(artifact)) {
    throw new Error(`Missing compiled Maryk browser artifact: ${artifact}. Run :store:indexeddb:jsTest :store:indexeddb:wasmJsTest :store:indexeddb:wasmJsBrowserDevelopmentLibraryDistribution first.`);
  }
}

const server = createServer((request, response) => {
  const path = new URL(request.url, "http://127.0.0.1").pathname;
  const root = Object.entries(artifacts).find(([prefix]) => path.startsWith(prefix));
  if (!root) {
    response.writeHead(200, { "content-type": "text/html" });
    response.end(`<!doctype html>
      <script type="importmap">{
        "imports": {
          "@js-joda/core": "/wasm-node-modules/@js-joda/core/dist/js-joda.esm.js"
        }
      }</script>
      <title>Maryk native IndexedDB smoke</title>`);
    return;
  }

  const [prefix, directory] = root;
  const file = resolve(directory, path.slice(prefix.length));
  if (!file.startsWith(`${directory}${sep}`) || !existsSync(file)) {
    response.writeHead(404).end();
    return;
  }

  response.writeHead(200, {
    "content-type": file.endsWith(".wasm") ? "application/wasm" : "text/javascript",
  });
  createReadStream(file).pipe(response);
});

await new Promise((resolveServer) => server.listen(0, "127.0.0.1", resolveServer));
const { port } = server.address();
const browser = await webkit.launch();

try {
  const page = await browser.newPage();
  await page.goto(`http://127.0.0.1:${port}`);
  await page.evaluate(() => {
    globalThis.define = undefined;
    globalThis.exports = undefined;
  });
  await page.evaluate(async () => {
    globalThis["@js-joda/core"] = await import("/wasm-node-modules/@js-joda/core/dist/js-joda.esm.js");
  });

  for (const script of [
    "kotlin/kotlin-kotlin-stdlib.js",
    "kotlin/kotlinx-atomicfu.js",
    "kotlin/kotlinx-coroutines-core.js",
    "kotlin/kotlinx-serialization-kotlinx-serialization-core.js",
    "kotlin/Kotlin-Immutable-Collections-kotlinx-collections-immutable.js",
    "kotlin/maryk-lib.js",
    "kotlin/maryk-json.js",
    "kotlin/maryk-yaml.js",
    "kotlin/Kotlin-DateTime-library-kotlinx-datetime.js",
    "kotlin/maryk-core.js",
    "kotlin/maryk-store-shared.js",
    "kotlin/maryk-store-indexeddb.js",
  ]) {
    await page.addScriptTag({ url: `http://127.0.0.1:${port}/js/${script}` });
  }

  await page.evaluate(async () => {
    const run = (entrypoint, databaseName) => new Promise((resolve, reject) => {
      entrypoint(databaseName, resolve, (message) => reject(new Error(message)));
    });
    const jsModule = globalThis["maryk-store-indexeddb"];
    const jsEntrypoint = jsModule?.maryk?.datastore?.indexeddb?.runMarykIndexedDbBrowserSmoke;
    if (typeof jsEntrypoint !== "function") {
      throw new Error(`Compiled Maryk JS smoke entrypoint is missing; globals: ${Object.keys(globalThis).filter((key) => key.includes("maryk")).join(",")}`);
    }
    const { runMarykIndexedDbWasmBrowserSmoke } = await import("/wasm/maryk-store-indexeddb.mjs");
    if (typeof runMarykIndexedDbWasmBrowserSmoke !== "function") throw new Error("Compiled Maryk Wasm smoke entrypoint is missing");
    try {
      await run(runMarykIndexedDbWasmBrowserSmoke, `maryk-wasm-smoke-${Date.now()}`);
    } catch (error) {
      throw new Error(`Compiled Maryk Wasm smoke failed: ${error.message}`);
    }
    try {
      await run(jsEntrypoint, `maryk-js-smoke-${Date.now()}`);
    } catch (error) {
      throw new Error(`Compiled Maryk JS smoke failed: ${error.message}`);
    }
  });
} finally {
  await browser.close();
  await new Promise((resolveServer, reject) => server.close((error) => error ? reject(error) : resolveServer()));
}
