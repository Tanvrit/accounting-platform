// Emit the SQL.js binary paired with the resolved browser or default entry.
config.module.rules.push({
    test: /[\\/]sql\.js[\\/]dist[\\/]sql-wasm(?:-browser)?\.js$/,
    use: [require('node:path').resolve(__dirname, '../../../../composeApp/webpack/sqljs-runtime-loader.cjs')],
});
