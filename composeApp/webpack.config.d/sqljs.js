// Copy sql.js WASM binary and JS to output directory for the web worker driver
// (Ported from platforms/ai — required so offline storage works on wasmJs instead of
// degrading to the volatile fallback in app/SdkInit.kt.)
const CopyWebpackPlugin = require("copy-webpack-plugin");

config.plugins.push(
    new CopyWebpackPlugin({
        patterns: [
            {
                from: "../../node_modules/sql.js/dist/sql-wasm.wasm",
                to: "."
            },
            {
                from: "../../node_modules/sql.js/dist/sql-wasm.js",
                to: "."
            }
        ]
    })
);
