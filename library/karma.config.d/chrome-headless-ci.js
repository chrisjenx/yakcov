// Chrome for Testing (installed by browser-actions/setup-chrome v2) refuses to start under the
// default ChromeHeadless launcher inside CI sandboxes ("Cannot start ChromeHeadless"). Add the
// standard CI-safe flags. Harmless locally. Applies to both the js and wasmJs browser tests.
config.set({
    customLaunchers: {
        ChromeHeadlessNoSandbox: {
            base: 'ChromeHeadless',
            flags: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'],
        },
    },
    browsers: ['ChromeHeadlessNoSandbox'],
});
