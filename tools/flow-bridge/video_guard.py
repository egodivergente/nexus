"""Refuse wrong video tiers/counts before Google receives the migrated RPC."""
import json
import re

MODEL_CODES = {
    'veo_3_1_fast': {'veo_3_1_t2v_fast_ultra', 'veo_3_1_t2v_fast'},
    'veo_3_1_quality': {'veo_3_1_t2v'},
    'veo_3_1_lite': {'veo_3_1_t2v_lite'},
    'omni_flash': {'abra_t2v_8s'},
}

def unpack(body):
    start = body.index('f.req=') + len('f.req=')
    envelope,_ = json.JSONDecoder().raw_decode(body[start:])
    if len(envelope)!=1 or len(envelope[0])!=1:raise ValueError('Multiple RPCs')
    entry=envelope[0][0]
    rows=json.loads(entry[1])[0]
    if len(rows)!=1:raise ValueError('Multiple outputs')
    return entry[0],rows[0]

def codes(requested, mode, duration, end, resolution):
    seconds=duration or 8
    if requested=='omni_flash':
        prefix='omni_flash_i2v_'+str(seconds)+'s_first_last' if end else 'abra_'+mode+'_'+str(seconds)+'s'
        return {prefix+('_360p' if resolution=='360p' else '')}
    if requested not in MODEL_CODES:return set()
    if mode=='t2v':
        base=MODEL_CODES[requested]
        if requested=='veo_3_1_quality' and seconds!=8:base={'veo_3_1_t2v_quality'}
    elif mode=='i2v':
        base={'veo_3_1_fast':{'veo_3_1_i2v_s_fast','veo_3_1_i2v_s_fast_ultra'},
            'veo_3_1_quality':{'veo_3_1_i2v_s','veo_3_1_i2v_s_quality'},'veo_3_1_lite':{'veo_3_1_i2v_lite','veo_3_1_i2v_s_lite'}}[requested]
        if end:
            if requested=='veo_3_1_lite':return {'veo_3_1_interpolation_lite'} if seconds==8 else {'veo_3_1_i2v_s_lite_'+str(seconds)+'s_fl'}
            base={b+'_fl' for b in base}
    elif mode=='r2v':
        base={'veo_3_1_fast':{'veo_3_1_r2v_fast_landscape','veo_3_1_r2v_fast_landscape_ultra',
            'veo_3_1_r2v_fast_portrait','veo_3_1_r2v_fast_portrait_ultra'},
            'veo_3_1_quality':set(),'veo_3_1_lite':{'veo_3_1_r2v_lite','veo_3_1_r2v_lite_low_priority'}}[requested]
    else:return set()
    if seconds!=8:
        if end:return {b[:-3]+'_'+str(seconds)+'s_fl' for b in base}
        return {b+'_'+str(seconds)+'s' for b in base}
    return base

def _single_model_problem(body, requested='veo_3_1_fast', *, mode='t2v', duration=None,
                  resolution=None,aspect=None,start_id=None,end_id=None,reference_ids=(),voice=None):
    try:
        rpc,row=unpack(body)
        expected_rpc={'t2v':'YhhmEf','i2v':'nprQif' if end_id else 'eb1hJf','r2v':'MZZa6b'}.get(mode)
        if rpc!=expected_rpc:raise ValueError('Unexpected modality RPC')
        code=row[2] if mode=='r2v' else row[1]
        if code not in codes(requested,mode,duration,bool(end_id),resolution):
            return "Unexpected video tier or output count; submission refused."
        if aspect is not None and row[3 if mode=='r2v' else 2]!={'landscape':2,'portrait':1}.get(aspect):
            return 'Requested aspect ratio was not bound; submission refused.'
        if mode=='i2v':
            if not start_id or row[4][1]!=start_id:return 'Start frame was not bound; submission refused.'
            if end_id and row[5][1]!=end_id:return 'End frame was not bound; submission refused.'
        if mode=='r2v':
            actual=[ref[1] for ref in row[1]]
            if not reference_ids or len(actual)!=len(reference_ids) or set(actual)!=set(reference_ids):
                return 'Requested references were not bound; submission refused.'
            actual_voice=row[7] if len(row)>7 else None
            if (voice is None and actual_voice not in (None,[])) or (voice is not None and actual_voice!=[[voice]]):
                return 'The requested preset voice was not bound; submission refused.'
    except (ValueError, TypeError, IndexError, KeyError):
        return "Video model cannot be verified from its RPC structure; submission refused."
    return None

def model_problem(body, requested='veo_3_1_fast', *, count=1, **options):
    try:
        start=body.index('f.req=')+len('f.req=')
        envelope,_=json.JSONDecoder().raw_decode(body[start:])
        if len(envelope)!=1 or len(envelope[0])!=1:raise ValueError()
        entry=envelope[0][0]
        payload=json.loads(entry[1])
        rows=payload[0]
        if type(count) is not int or not 1<=count<=4 or len(rows)!=count:raise ValueError()
        for row in rows:
            single='f.req='+json.dumps([[[entry[0],json.dumps([[row],*payload[1:]])]]])
            problem=_single_model_problem(single,requested,**options)
            if problem:return problem
    except (TypeError,ValueError,IndexError,KeyError):
        return 'Requested video count was not bound; submission refused.'
    return None

def interpolation_problem(body,rpc,start_id,end_id):
    """Structural frame check for the measured *_fl and first_last RPCs."""
    try:
        start=body.index('f.req=')+len('f.req=')
        envelope,_=json.JSONDecoder().raw_decode(body[start:])
        if len(envelope)!=1 or len(envelope[0])!=1:raise ValueError()
        entry=envelope[0][0];actual=entry[0];rows=json.loads(entry[1])[0]
        if not 1<=len(rows)<=4:raise ValueError()
        if any(row[4][1]!=start_id or row[5][1]!=end_id for row in rows):raise ValueError()
        row=rows[0]
        if actual!=rpc or rpc!='nprQif' or not start_id or not end_id:raise ValueError()
        code=row[1]
        if not isinstance(code,str) or not (code.endswith('_fl') or 'first_last' in code or 'interpolation' in code):raise ValueError()
        if row[4][1]!=start_id or row[5][1]!=end_id:raise ValueError()
    except (TypeError,ValueError,IndexError,KeyError):return 'Both requested frames must be structurally bound before submission.'
    return None
