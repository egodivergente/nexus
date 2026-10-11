"""Add current Android snapshot IDs to the complete Desktop catalog, preserving PC contracts."""
from pathlib import Path
import copy,json
root=Path(__file__).resolve().parents[2]
path=root/'desktop/ui/catalog.json';catalog=json.loads(path.read_text('utf-8-sig'))
snapshot=json.loads((root/'app/src/main/assets/atlas_catalog_snapshot.json').read_text('utf-8'))['data']
known={m['id'] for m in catalog if m['providerId']=='atlas_cloud'};added=[]
for item in snapshot:
    if item['model'] in known:continue
    if item['type']!='Text':raise ValueError('New non-chat model needs contract review: '+item['model'])
    template=next(m for m in catalog if m['providerId']=='atlas_cloud' and m['modalities']==['CHAT'])
    model=copy.deepcopy(template)
    model.update(id=item['model'],providerModelId=None,displayName=item['displayName'],organization=item['organization'].title(),uuid=item['uuid'],
        description=item.get('profile',''),avatarUrl=item.get('avatar'),coverUrl=item.get('coverUrl'),schemaUrl=item.get('schema'),
        categories=item.get('categories',[]),tags=item.get('tags',[]),basePriceUsd=None,originPriceUsd=None,pricingInfo='Consultar coste con la configuración',
        priceUnit='unidad sin confirmar',priceUnitVerified=False,discount=item.get('price',{}).get('discount'),
        modelFamily='Claude' if 'claude' in item['model'] else 'DeepSeek',family=None,releaseDateMillis=None,versionNumeric=None,
        isFlagship=False,providerPriority=item.get('priority',0),parameters=[],chatSamplingParameters=[],nativeRequiredFields=[],badge=None,
        supportsImageInput='image' in item.get('input_modalities',[]),shortDescription='Modelo de lenguaje. Opciones y disponibilidad según la ficha del proveedor.')
    catalog.append(model);added.append(model['id'])
path.write_text(json.dumps(catalog,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
print(json.dumps({'catalogModels':len(catalog),'addedCurrentAndroidIds':added}))
