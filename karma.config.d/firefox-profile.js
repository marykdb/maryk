// Firefox's -profile does not isolate its app-data directory on macOS.
// https://github.com/microsoft/playwright/issues/42768
// Cache only that bookkeeping per task; Karma still creates fresh browser profiles.
if (process.platform === 'darwin' && !process.env.CFFIXED_USER_HOME) {
    const fs = require('fs');
    const os = require('os');
    const path = require('path');
    const taskHash = require('crypto').createHash('sha256').update(__dirname).digest('hex').slice(0, 16);
    const firefoxHome = path.join(os.tmpdir(), `maryk-karma-firefox-${taskHash}`);
    fs.mkdirSync(firefoxHome, { recursive: true });
    process.env.CFFIXED_USER_HOME = firefoxHome;
}
