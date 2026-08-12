import { webkit } from "playwright";
import { createServer } from "node:http";

const databaseName = `maryk-indexeddb-smoke-${Date.now()}`;
const server = createServer((_, response) => {
  response.writeHead(200, { "content-type": "text/html" });
  response.end("<!doctype html><title>Maryk IndexedDB smoke</title>");
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const { port } = server.address();
const browser = await webkit.launch();

try {
  const page = await browser.newPage();
  await page.goto(`http://127.0.0.1:${port}`);
  await page.evaluate(async (name) => {
    const request = indexedDB.open(name, 1);
    request.onupgradeneeded = () => request.result.createObjectStore("records");

    const database = await new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });

    const write = database.transaction("records", "readwrite");
    write.objectStore("records").put("value", "key");
    await new Promise((resolve, reject) => {
      write.oncomplete = resolve;
      write.onerror = () => reject(write.error);
      write.onabort = () => reject(write.error);
    });

    const read = database.transaction("records").objectStore("records").get("key");
    const value = await new Promise((resolve, reject) => {
      read.onsuccess = () => resolve(read.result);
      read.onerror = () => reject(read.error);
    });
    if (value !== "value") throw new Error(`Unexpected IndexedDB value: ${value}`);

    database.close();
    const deletion = indexedDB.deleteDatabase(name);
    await new Promise((resolve, reject) => {
      deletion.onsuccess = resolve;
      deletion.onerror = () => reject(deletion.error);
    });
  }, databaseName);
} finally {
  await browser.close();
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
}
