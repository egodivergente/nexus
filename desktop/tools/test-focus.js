const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const ui=path.join(__dirname,'../ui');
const catalog=JSON.parse(fs.readFileSync(path.join(ui,'catalog.json'),'utf8'));
const ctx=vm.createContext({renderCatalog(){},document:{addEventListener(){}},state:{favorites:[],hidden:[]},models:catalog,kind:'IMAGE',query:'',familyFilter:'',provider:'',onlyFavorites:false,onlyDated:false,showHidden:false,sort:'recommended'});
vm.runInContext(fs.readFileSync(path.join(ui,'focus.js'),'utf8'),ctx);
const focus=vm.runInContext('NexusFocus',ctx);
const ids=focus.entries.map(entry=>entry[0]);
assert.equal(new Set(ids).size,ids.length,'La portada no debe repetir modelos');
for(const id of ids) assert(catalog.some(model=>model.id===id),`Falta la ruta ${id}`);
for(const modality of ['IMAGE','VIDEO']) {
  const visible=focus.curated(catalog,modality,[],[]);
  assert(visible.length>=5,`Selección ${modality} vacía`);
  assert(focus.grouped(visible).length<visible.length,'Las variantes deben quedar dentro del mismo modelo');
  const first=visible[0].model.id;
  assert(!focus.curated(catalog,modality,[first],[]).some(entry=>entry.model.id===first),'Un modelo oculto ha reaparecido');
  assert(visible.every(entry=>entry.good&&entry.avoid),'Falta orientación de uso');
}
console.log(`PASS selección: ${ids.length} rutas reales, sin duplicados, con límites y ocultación respetada; 0 llamadas de red`);
