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

    function record(action) {
        try {
            action();
        } catch (error) {
            console.error(`Maryk Karma diagnostics reporter failed: ${error}`);
        }
    }

    function MarykKarmaDiagnosticsReporter() {
        this.onBrowserStart = (browser) => record(() => writeDiagnostic('browser-start', browser, ''));
        this.onSpecComplete = (browser, result) => {
            record(() => {
                const description = Array.isArray(result.description) ? result.description.join(' > ') : result.description;
                lastCompletedTests.set(browser.id, description);
                if (!result.success) {
                    writeDiagnostic('test-failed', browser, description);
                }
            });
        };
        this.onBrowserError = (browser, error) => {
            record(() => writeDiagnostic('browser-error', browser, `${error}; last-test=${lastCompletedTests.get(browser.id) || 'none'}`));
        };
        this.onBrowserComplete = (browser, result) => {
            record(() => writeDiagnostic('browser-complete', browser, `${JSON.stringify(result)}; last-test=${lastCompletedTests.get(browser.id) || 'none'}`));
        };
    }

    MarykKarmaDiagnosticsReporter.$inject = [];
    config.plugins = config.plugins || [];
    config.plugins.push({ 'reporter:maryk-karma-diagnostics': ['type', MarykKarmaDiagnosticsReporter] });
    config.reporters = config.reporters || [];
    config.reporters.push('maryk-karma-diagnostics');
}
