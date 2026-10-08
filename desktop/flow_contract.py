"""Explicit Flow generation contracts shared by the desktop and private bridge."""
import hashlib
import json

IMAGES = {'nano2', 'nano-pro'}
VIDEOS = {'veo_fast', 'veo_quality', 'veo_lite', 'omni_flash'}
INPUTS = ('initial_frame', 'end_frame', 'reference_images')

def block_counts(count):
    if type(count) is not int or not 1 <= count <= 120:
        raise ValueError('La cantidad debe ser un entero entre 1 y 120.')
    while count:
        size = min(4, count)
        yield size
        count -= size

def normalize(args):
    model = args.get('model')
    image = model in IMAGES
    if not image and model not in VIDEOS:
        raise ValueError('Modelo Flow no admitido.')
    aspect = args.get('aspect', '16:9')
    if aspect not in ({'1:1','16:9','9:16','4:3','3:4'} if image else {'16:9','9:16'}):
        raise ValueError('Formato Flow no admitido.')
    mode = args.get('mode', 't2i' if image else 't2v')
    if mode not in ({'t2i'} if image else {'t2v','i2v','r2v'}):
        raise ValueError('Modalidad Flow no admitida.')
    refs = args.get('reference_images', [])
    if not isinstance(refs, list) or any(not isinstance(r,str) or not r for r in refs) or len(set(refs))!=len(refs):
        raise ValueError('Referencias Flow no válidas o repetidas.')
    maximum = 10 if image else 7 if model=='omni_flash' else 0 if model=='veo_quality' else 3
    if len(refs)>maximum:
        raise ValueError(f'Este modelo admite hasta {maximum} referencias.')
    start, end = args.get('initial_frame'), args.get('end_frame')
    if any(v is not None and (not isinstance(v,str) or not v) for v in (start,end)):
        raise ValueError('Fotograma Flow no válido.')
    if image and (start or end) or not image and mode=='t2v' and (start or end or refs):
        raise ValueError('Esta modalidad no admite fotogramas ni referencias.')
    if not image and mode=='i2v' and (not start or refs):
        raise ValueError('Añade el fotograma inicial; las referencias usan otra modalidad.')
    if not image and mode=='r2v' and (not refs or start or end):
        raise ValueError('Añade referencias; esta modalidad no admite fotogramas.')
    result = {'model':model, 'aspect':aspect}
    voice=args.get('voice')
    if voice not in (None,'','Automática'):
        names={'Achernar','Achird','Algenib','Algieba','Alnilam','Aoede','Autonoe','Callirrhoe',
            'Charon','Despina','Enceladus','Erinome','Fenrir','Gacrux','Iapetus'}
        if model!='omni_flash' or mode!='r2v' or voice not in names:
            raise ValueError('La voz predefinida está disponible con referencias de Omni.')
        result['voice']=voice
    if not image and mode!='t2v':result['mode']=mode
    if refs:result['reference_images']=refs
    if start:result['initial_frame']=start
    if end:result['end_frame']=end
    duration = args.get('duration')
    if duration is not None:
        if image or type(duration) is not int or duration not in ({4,6,8,10} if model=='omni_flash' else {8} if mode=='r2v' or model=='veo_quality' else {4,6,8}):
            raise ValueError('Duración no disponible para este modelo y modalidad.')
        result['duration']=duration
    resolution = args.get('resolution')
    if resolution is not None:
        if image or resolution not in ({'360p','720p'} if model=='omni_flash' else {'720p'}):
            raise ValueError('Resolución nativa no disponible. Las mejoras se solicitan sobre el resultado.')
        # Veo has no native resolution control: 720p is its native output.
        if model=='omni_flash':result['resolution']=resolution
    count = args.get('count', 1)
    next(block_counts(count))
    if count != 1:result['count'] = count
    return result

def quote_key(params):
    """No price can be reused across modes, lengths, formats or resolutions."""
    effective = normalize(params)
    if effective['model'] in VIDEOS:
        effective.setdefault('mode','t2v')
        effective.setdefault('duration',8)
        if effective['model']=='omni_flash':effective.setdefault('resolution','720p')
    return hashlib.sha256(json.dumps(effective,sort_keys=True,separators=(',',':')).encode()).hexdigest()
