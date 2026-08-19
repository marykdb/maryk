if (process.env.MARYK_KARMA_DIAGNOSTICS_DIR) {
    const fs = require('fs');
    const path = require('path');
    const diagnosticsDirectory = process.env.MARYK_KARMA_DIAGNOSTICS_DIR;
    const lastCompletedTests = new Map();

    fs.mkdirSync(diagnosticsDirectory, { recursive: true });

    function writeDiagnostic(event, browser, detail) {
        const fileName = `${browser.id || browser.name}.log`.replace(/[^a-zA-Z0-9_.-]/g, '_');
        const message = `${new Date().toISOString()} ${event} browser=${browser.name} ${detail}\n`;
        fs.appendFileSync(path.join(diagnosticsDirectory, fileName), message);
    }

    function MarykKarmaDiagnosticsReporter() {
        this.onBrowserStart = (browser) => writeDiagnostic('browser-start', browser, '');
        this.onSpecComplete = (browser, result) => {
            const description = result.description.join(' > ');
            lastCompletedTests.set(browser.id, description);
            if (!result.success) {
                writeDiagnostic('test-failed', browser, description);
            }
        };
        this.onBrowserError = (browser, error) => {
            writeDiagnostic('browser-error', browser, `${error}; last-test=${lastCompletedTests.get(browser.id) || 'none'}`);
        };
        this.onBrowserComplete = (browser, result) => {
            writeDiagnostic('browser-complete', browser, `${JSON.stringify(result)}; last-test=${lastCompletedTests.get(browser.id) || 'none'}`);
        };
    }

    MarykKarmaDiagnosticsReporter.$inject = [];
    config.plugins = config.plugins || [];
    config.plugins.push({ 'reporter:maryk-karma-diagnostics': ['type', MarykKarmaDiagnosticsReporter] });
    config.reporters = config.reporters || [];
    config.reporters.push('maryk-karma-diagnostics');
}
