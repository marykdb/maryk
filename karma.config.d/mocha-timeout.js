// Isolate each suite from browsers left connected by an earlier test run.
config.port = 0;

config.client = config.client || {};
config.client.mocha = config.client.mocha || {};
config.client.mocha.timeout = 180000;
// Let the test timeout report a stalled IndexedDB operation before Karma disconnects the browser.
config.browserNoActivityTimeout = 210000;
