config.client = config.client || {};
config.client.mocha = config.client.mocha || {};
config.client.mocha.timeout = 180000;
// Let the test timeout report a stalled IndexedDB operation before Karma disconnects the browser.
config.browserNoActivityTimeout = 210000;
