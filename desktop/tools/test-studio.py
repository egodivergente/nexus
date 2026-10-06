"""Isolated desktop interaction checks. No external or paid requests."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[2]
out = root / 'outputs/studio-redesign-20261006'
session = json.loads((out / 'qa-data/local-session.json').read_text())
errors = []
with sync_playwright() as p:
    browser = p.chromium.launch(channel='msedge', headless=True)
    page = browser.new_page(viewport={'width':1360,'height':900}, bypass_csp=True)
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.route('https://**/*', lambda route: route.abort())
    page.route('https://example.invalid/**', lambda route: route.fulfill(path=str(root/'desktop/ui/logos/flux.png'),content_type='image/png'))
    page.goto(f"http://127.0.0.1:{session['port']}/")
    page.wait_for_function('Boolean(window.nexusQA)')
    assert page.locator('#navigation button').count() == 3
    assert page.evaluate('Agent.snapshot===null')
    page.evaluate("""()=>{
      state.jobs=[{id:'one',name:'Prueba de imagen',modelId:'fixture',providerId:'atlas_cloud',prompt:'Un paisaje de prueba',
       modality:'IMAGE',status:'COMPLETED',outputs:['https://example.invalid/one.png'],parameters:{seed:42}},
       {id:'two',name:'Prueba en curso',modelId:'fixture',providerId:'atlas_cloud',prompt:'Pendiente',modality:'VIDEO',status:'PROCESSING',outputs:[]}];
      render();
    }""")
    assert page.locator('#content .result').count()==1
    page.locator('[data-studio-tab=activity]').click()
    assert page.locator('#content .result').count()==2
    page.locator('[data-studio-tab=results]').click()
    page.locator('[data-studio-detail=one]').click()
    assert page.locator('.studio-prompt').inner_text()=='Un paisaje de prueba'
    assert page.locator('[data-studio-reference=one]').count()==1
    page.screenshot(path=str(out/'library.png'))
    page.locator('[data-studio=close-detail]').click()
    page.locator('[data-nav=canvas]').click()
    page.locator('#prompt').fill('Una imagen de prueba')
    page.locator('[data-studio=picker]').last.click()
    page.locator('#studio-model-search').fill('seedream')
    assert page.locator('[data-studio-model]').count()>0
    page.screenshot(path=str(out/'model-picker.png'))
    page.locator('[data-studio-model]').first.click()
    page.wait_for_function('loaded && !configuring')
    assert page.locator('#prompt').input_value()=='Una imagen de prueba'
    assert page.locator('#generate').is_disabled()
    page.screenshot(path=str(out/'create.png'))
    # Real local import through the authenticated host, no provider upload.
    imported=page.evaluate("""async()=>{
      const response=await fetch('logos/flux.png'),blob=await response.blob();
      const content=await new Promise(resolve=>{const r=new FileReader();r.onload=()=>resolve(r.result.split(',')[1]);r.readAsDataURL(blob);});
      const file=await rpc('importMedia',{name:'reference.png',mime:'image/png',content});
      const check=await fetch(file.url);return {bytes:file.size,status:check.status};
    }""")
    assert imported['bytes']>0 and imported['status']==200
    rejected=page.evaluate("""async()=>{
      try{await rpc('importMedia',{name:'bad.exe',mime:'image/png',content:'YQ=='});return false;}catch{return true;}
    }""")
    assert rejected
    # Seed a compatible field, then exercise the same drop/paste import handler.
    page.evaluate("state.draft.media=[];loaded.fields=[{key:'image',label:'Imagen',mime:'image/*',maximum:2,required:false}];renderCanvas()")
    page.evaluate("""async()=>{
      const blob=await (await fetch('logos/flux.png')).blob();
      await studioAttach([new File([blob],'reference.png',{type:'image/png'})],'image');
    }""")
    assert page.evaluate('state.draft.media.length')==1
    assert page.evaluate('quote===null')
    page.evaluate("""async()=>{
      const blob=await (await fetch('logos/flux.png')).blob(),data=new DataTransfer();
      data.items.add(new File([blob],'pasted.png',{type:'image/png'}));
      document.querySelector('#prompt').dispatchEvent(new ClipboardEvent('paste',{clipboardData:data,bubbles:true,cancelable:true}));
    }""")
    page.wait_for_function('state.draft.media.length===2 && !Studio.importing')
    page.evaluate("state.draft.media=[];renderCanvas()")
    page.evaluate("""async()=>{
      const blob=await (await fetch('logos/flux.png')).blob(),data=new DataTransfer();
      data.items.add(new File([blob],'dropped.png',{type:'image/png'}));
      document.querySelector('.drop').dispatchEvent(new DragEvent('drop',{dataTransfer:data,bubbles:true,cancelable:true}));
    }""")
    page.wait_for_function('state.draft.media.length===1 && !Studio.importing')
    page.set_viewport_size({'width':760,'height':900})
    page.screenshot(path=str(out/'create-narrow.png'))
    assert not page.evaluate('document.documentElement.scrollWidth>innerWidth')
    # Reference reuse only stages a URL; it never submits a job.
    page.locator('[data-nav=library]').click()
    page.locator('[data-studio-detail=one]').click()
    page.locator('[data-studio-reference=one]').click()
    assert page.locator('#studio-model-search').is_visible()
    assert page.evaluate("Studio.reference.remoteUrl==='https://example.invalid/one.png'")
    page.locator('[data-action=close]').click()
    assert page.evaluate('Studio.reference===null')
    page.evaluate("""async()=>{
      const id=loaded.id;state.draft.values.seed=12345;
      const other=models.find(m=>m.id!==id && m.providerId==='atlas_cloud' && m.modalities.includes('IMAGE'));
      await openModel(other.id);await openModel(id);
      if(state.draft.values.seed!==12345)throw Error('Model settings were lost');
    }""")
    page.locator('[data-nav=assistant]').click()
    page.wait_for_function('Agent.snapshot && !Agent.busy', timeout=45000)
    page.screenshot(path=str(out/'assistant-narrow.png'))
    assert not errors, errors
    report={'navigation':3,'activityIntegrated':True,'promptPreserved':True,'localImport':imported,
            'invalidImportRejected':rejected,'referenceStagedOnly':True,'assistantSkills':page.evaluate('Agent.snapshot.skills.length'),
            'errors':errors,'paidCalls':0}
    (out/'verification.json').write_text(json.dumps(report,indent=2))
    print(json.dumps(report))
    browser.close()

