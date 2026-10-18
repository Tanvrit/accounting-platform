const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

// Package the binary paired with the resolved SQL.js entrypoint, including
// the browser export introduced in SQL.js 1.15. Never mix Emscripten builds.
module.exports = function sqlJsRuntimeLoader(source) {
    const entry = path.basename(this.resourcePath);
    if (!/^sql-wasm(?:-browser)?\.js$/.test(entry)) {
        throw new Error('Unrecognized SQL.js entrypoint; review browser packaging.');
    }
    const runtimeName = entry.replace(/\.js$/, '.wasm');
    if (!source.includes(runtimeName)) {
        throw new Error('SQL.js runtime reference changed; review browser packaging.');
    }
    const runtimePath = path.join(path.dirname(this.resourcePath), runtimeName);
    this.addDependency(runtimePath);
    const runtime = fs.readFileSync(runtimePath);
    const hash = crypto.createHash('sha256').update(runtime).digest('hex').slice(0, 16);
    const filename = `${runtimeName.slice(0, -5)}.${hash}.wasm`;
    this.emitFile(filename, runtime);
    return source.replaceAll(runtimeName, filename);
};
