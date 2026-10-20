"""Local shot boundaries and timestamped visual evidence, never semantic guesses."""
import json
import math
import re
import uuid


def detect(native, args, assets):
    from PIL import Image, ImageDraw
    parent, source = native.asset(args['asset_id'], assets)
    info = native.inspect(source)
    if not info.get('width'):
        raise ValueError('Selecciona un vídeo con pista de imagen.')
    start = int(args.get('start_ms', 0))
    end = int(args.get('end_ms', min(start + 600000, info['duration_ms'])))
    threshold = float(args.get('threshold', .3))
    if start < 0 or end <= start or end > info['duration_ms'] or end - start > 600000:
        raise ValueError('Analiza un intervalo dentro del vídeo, de hasta 10 minutos por encargo.')
    if not math.isfinite(threshold) or not .05 <= threshold <= .9:
        raise ValueError('El umbral debe estar entre 0,05 y 0,9.')
    metadata = native.directory / (uuid.uuid4().hex + '.scenes')
    try:
        # Use a relative filename from a fixed cwd to avoid Windows colon/filter escaping.
        command = [native.ffmpeg(), '-hide_banner', '-loglevel', 'error', '-nostdin', '-y', '-threads', '2',
                   '-filter_threads', '2', '-ss', str(start/1000), '-i', str(source), '-t', str((end-start)/1000),
                   '-an', '-vf', f"scale=320:-2,select='gt(scene,{threshold})',metadata=print:file={metadata.name}",
                   '-f', 'null', '-']
        import subprocess
        response = subprocess.run(command, cwd=native.directory, capture_output=True, timeout=3600,
                                  creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        if response.returncode:
            raise ValueError('No se pudieron detectar los cambios de plano: ' + response.stderr.decode('utf-8', errors='replace')[-1500:])
        cuts = [start]
        for value in re.findall(r'pts_time:([0-9.]+)', metadata.read_text('utf-8') if metadata.exists() else ''):
            timestamp = start + round(float(value)*1000)
            if cuts[-1] + 250 <= timestamp < end - 100:
                cuts.append(timestamp)
        # Include regular evidence inside long uncut shots. At most 24 panels per result.
        intervals = []
        for head, tail in zip(cuts, cuts[1:] + [end]):
            for step in range(max(1, math.ceil((tail-head)/20000))):
                a, b = head + step*20000, min(tail, head + (step+1)*20000)
                intervals.append({'start_ms': a, 'end_ms': b, 'sample_ms': (a+b)//2,
                                  'shot_start_ms': head, 'shot_end_ms': tail})
        selected = intervals[:24]
        results = []
        for page in range(0, len(selected), 12):
            group = selected[page:page+12]
            sheet = Image.new('RGB', (3*336, math.ceil(len(group)/3)*216), '#12151b')
            draw = ImageDraw.Draw(sheet)
            for number, interval in enumerate(group):
                import io
                content = native.encode(['-ss', interval['sample_ms']/1000, '-i', source, '-frames:v', '1',
                                         '-vf', 'scale=320:180:force_original_aspect_ratio=decrease',
                                         '-f', 'image2pipe', '-vcodec', 'mjpeg', 'pipe:1'], 60)
                with Image.open(io.BytesIO(content)) as frame:
                    x, y = (number%3)*336, (number//3)*216
                    sheet.paste(frame, (x+(336-frame.width)//2, y))
                draw.text((x+8, y+184), f"{page+number+1}: {interval['sample_ms']/1000:.3f} s", fill='white')
                draw.text((x+8, y+199), f"{interval['start_ms']/1000:.3f} - {interval['end_ms']/1000:.3f} s", fill='white')
            target = native.new_path('.jpg')
            sheet.save(target, quality=88)
            asset = native.output(target, parent['displayName'] + ' · escenas ' + str(page//12+1), parent, 'IMAGE')
            asset['tags'] = ['scene-contact-sheet']
            results.append(asset)
        coverage_end = selected[-1]['end_ms']
        report = {'asset_id': parent['id'], 'duration_ms': info['duration_ms'], 'threshold': threshold,
                  'analyzed_start_ms': start, 'analyzed_end_ms': end, 'visual_coverage_end_ms': coverage_end,
                  'next_start_ms': coverage_end if coverage_end < info['duration_ms'] else None,
                  'detected_cuts_ms': cuts[1:], 'samples': selected,
                  'limitations': 'Cambios de plano por diferencia visual; los paneles son muestras. Identifica el contenido viendo las hojas y refina los tiempos con extract_video_frames antes de recortar. No identifica diálogos ni garantiza ver acciones entre muestras.'}
        target = native.new_path('.json')
        target.write_text(json.dumps(report, ensure_ascii=False), encoding='utf-8')
        results.append(native.output(target, parent['displayName'] + ' · tiempos de escenas.json', parent, 'OTHER'))
        return results
    finally:
        metadata.unlink(missing_ok=True)
