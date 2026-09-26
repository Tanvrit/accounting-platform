// sql.js/dist references Node.js built-ins ('path', 'crypto') that are not available
// in the browser. Webpack 5 no longer polyfills these automatically. Setting them to
// false tells webpack to provide an empty module stub instead of an error.
// (Ported from platforms/ai — same sql.js wasm-storage path via the SDK.)
config.resolve = config.resolve || {};
config.resolve.fallback = Object.assign({}, config.resolve.fallback, {
    "path": false,
    "crypto": false,
    "fs": false,
});
