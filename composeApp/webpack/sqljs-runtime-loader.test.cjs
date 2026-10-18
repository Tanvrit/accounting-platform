const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const vm=require('node:vm');
const loader=require('./sqljs-runtime-loader.cjs');
for(const entry of ['sql-wasm.js','sql-wasm-browser.js']){
 test(`packages matching runtime for ${entry}`,()=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'sqljs-runtime-test-'));
  try{
   const dir=path.join(root,'node_modules/sql.js/dist');fs.mkdirSync(dir,{recursive:true});
   const name=entry.replace(/\.js$/,'.wasm');const runtime=Buffer.from(`matching runtime: ${entry}`);fs.writeFileSync(path.join(dir,name),runtime);
   const emitted=[];const dependencies=[];
   const source=`fetch("${name}"); fallback("${name}")`;
   const result=loader.call({resourcePath:path.join(dir,entry),addDependency:p=>dependencies.push(p),emitFile:(name,data)=>emitted.push({name,data})},source);
   assert.equal(emitted.length,1);assert.deepEqual(emitted[0].data,runtime);assert.match(emitted[0].name,/\.[a-f0-9]{16}\.wasm$/);assert.equal(dependencies[0],path.join(dir,name));assert.equal(result,source.replaceAll(name,emitted[0].name));
   const config={module:{rules:[]}};vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../webpack.config.d/sqljs-runtime.js'),'utf8'),{config,require,__dirname});assert.ok(config.module.rules[0].test.test(path.join(dir,entry)),'browser entry must pass webpack rule');
  }finally{fs.rmSync(root,{recursive:true,force:true});}
 });
}
test('rejects unknown entrypoint and changed upstream reference',()=>{
 assert.throws(()=>loader.call({resourcePath:'/sql.js/dist/unknown.js'},''),/Unrecognized/);
 assert.throws(()=>loader.call({resourcePath:'/sql.js/dist/sql-wasm-browser.js'},'different.wasm'),/reference changed/);
});
