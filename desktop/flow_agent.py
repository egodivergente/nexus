"""Keep agent discovery tied to the live Flow contract, including open windows."""
import flow_provider


def summary(model):
    fields = model.get('contractMedia', [])
    keys = {field['key'] for field in fields}
    return {
        'ref': {'providerId': model['providerId'], 'modelId': model['id']},
        'name': model['displayName'], 'organization': model['organization'],
        'family': model['modelFamily'], 'modalities': model['modalities'],
        'capabilities': model['capabilities'], 'inputMode': model['inputMode'],
        'purpose': model['shortDescription'], 'isFlagship': model['isFlagship'],
        'supportsImageInput': model['supportsImageInput'],
        'supportsFirstFrame': 'initial_frame' in keys,
        'supportsLastFrame': 'end_frame' in keys,
        'supportsReferenceImages': 'reference_images' in keys,
        'maxImages': model.get('maxImages', max((f['maximum'] for f in fields), default=0)),
        'mediaRequired': any(f.get('required') for f in fields),
        'catalogDurations': model.get('supportedDurations', []),
        'catalogResolutions': model.get('supportedResolutions', []),
        'priceUnitVerified': False,
    }


def schema(model):
    fields = model.get('contractMedia', [])
    first = next((f['key'] for f in fields if f['key'] == 'initial_frame'), None)
    last = next((f['key'] for f in fields if f['key'] == 'end_frame'), None)
    array = next((f['key'] for f in fields if f.get('encoding') == 'array'), None)
    media = {'inputMode': model['inputMode'],
             'maxImages': max((f['maximum'] for f in fields), default=0),
             'mediaRequired': any(f.get('required') for f in fields)}
    media.update({k: v for k, v in {'firstFrameField': first, 'lastFrameField': last,
                                  'inputImageField': first, 'imagesArrayField': array}.items() if v})
    params = []
    for p in model['parameters']:
        item = {'key': p['key'], 'displayName': p['displayName'], 'uiType': p['type'],
                'nativeType': p['nativeType'], 'options': p.get('options', []),
                'required': p.get('isRequired', False), 'advanced': p.get('isAdvanced', False)}
        for source, target in [('defaultValue', 'default'), ('minValue', 'min'),
                               ('maxValue', 'max'), ('step', 'step'), ('description', 'description')]:
            if p.get(source) is not None:
                item[target] = str(p[source]) if target == 'default' else p[source]
        params.append(item)
    return {'ref': {'providerId': model['providerId'], 'modelId': model['id']},
            'parameters': params, 'media': media, 'promptRequired': model['promptRequired'],
            'unsupportedRequired': [], 'textLimits': {}, 'source': 'Nexus live Flow contract'}


def reply(host, args, state):
    # Refresh only an outstanding callback owned by this window. Never turn an
    # unsolicited/repeated reply into access to the registry or a submission.
    with host.pending_lock:
        item = host.pending.get(args.get('id'))
        if args.get('owner') != host.owner or not item or item['event'].is_set():
            return False
        operation, reference = item['op'], item['args']
    if operation == 'models' and args.get('ok') is True:
        hidden = set((state or {}).get('hidden', []))
        current = [summary(m) for m in flow_provider.catalog() if m['id'] not in hidden]
        others = [m for m in args['value'] if m.get('ref', {}).get('providerId') != 'google_flow']
        args = {**args, 'value': others + current}
    elif operation == 'schema' and reference.get('providerId') == 'google_flow':
        model = next((m for m in flow_provider.catalog() if m['id'] == reference.get('modelId')), None)
        args = {**args, 'ok': model is not None,
                'value': schema(model) if model else None,
                'error': 'El modelo no está disponible en el catálogo actual de Flow.'}
    return host.reply(args)
