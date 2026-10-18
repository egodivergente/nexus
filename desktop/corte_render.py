"""Windows compositor. Kotlin supplies timing, layer, colour and audio evaluation.

Preview and export use this exact compositor. The browser only plays its output;
it does not attempt to approximate Corte effects with CSS or a single source clip.
"""
from collections import OrderedDict
from contextlib import ExitStack
import math
from pathlib import Path
import subprocess
import tempfile
from urllib.parse import unquote, urlparse
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageOps
from native_operations import FLAGS


def local_path(uri):
    parsed=urlparse(uri)
    if parsed.scheme != 'file' or parsed.netloc:
        raise ValueError('El render requiere un archivo local revinculado.')
    path=unquote(parsed.path)
    if len(path)>2 and path[0]=='/' and path[2]==':':
        path=path[1:]
    return Path(path)


class Decoder:
    def __init__(self, ffmpeg, media, size, fps):
        self.ffmpeg,self.media,self.size,self.fps=ffmpeg,media,size,fps
        self.process=None;self.index=-1;self.frame=None
    def close(self):
        if self.process:
            self.process.stdout.close()
            self.process.terminate()
            try:self.process.wait(timeout=3)
            except subprocess.TimeoutExpired:self.process.kill();self.process.wait()
            self.process=None
    def get(self, source_us):
        if self.media['kind']=='IMAGE':
            if self.frame is None:
                with Image.open(local_path(self.media['uri'])) as im:
                    self.frame=ImageOps.exif_transpose(im).convert('RGB').copy()
            return self.frame.copy()
        wanted=max(0,int(source_us*self.fps/1_000_000))
        if self.process is None or wanted<self.index:
            self.close();self.index=wanted-1
            width,height=self.size
            self.process=subprocess.Popen([self.ffmpeg,'-v','error','-nostdin','-threads','1','-ss',str(wanted/self.fps),
                '-i',str(local_path(self.media['uri'])),'-an','-vf',f'fps={self.fps},scale={width}:{height}:force_original_aspect_ratio=decrease',
                '-pix_fmt','rgb24','-threads','1','-f','rawvideo','pipe:1'],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,creationflags=FLAGS)
            ratio=min(width/max(1,self.media['width']),height/max(1,self.media['height']))
            self.decoded=(max(1,round(self.media['width']*ratio)),max(1,round(self.media['height']*ratio)))
        while self.index<wanted:
            length=self.decoded[0]*self.decoded[1]*3
            data=bytearray()
            while len(data)<length:
                part=self.process.stdout.read(length-len(data))
                if not part:break
                data.extend(part)
            if len(data)!=length:
                if self.frame is not None:return self.frame.copy()
                raise ValueError('No se pudo decodificar el medio '+self.media['displayName'])
            self.frame=Image.frombytes('RGB',self.decoded,bytes(data));self.index+=1
        return self.frame.copy()


def layer_image(im, layer, size):
    width,height=size;props=layer['props'];crop=props['crop']
    if any(crop.values()):
        w,h=im.size
        im=im.crop((round(crop['left']*w),round(crop['top']*h),round((1-crop['right'])*w),round((1-crop['bottom'])*h)))
    if props['flipH']:im=ImageOps.mirror(im)
    if props['flipV']:im=ImageOps.flip(im)
    matrix=layer.get('color_matrix')
    if matrix:
        pixels=np.asarray(im,dtype=np.float32)/255
        m=np.asarray(matrix,dtype=np.float32).reshape(3,4)
        corrected=pixels@m[:,:3].T+m[:,3]
        im=Image.fromarray(np.uint8(np.clip(corrected,0,1)*255))
    if props['color'].get('lutUri'):
        raise ValueError('LUT todavía no disponible en el adaptador Windows; no se exportará ignorándola.')
    mode=props['fit']
    if mode=='FILL':im=ImageOps.fit(im,size)
    elif mode=='STRETCH':im=im.resize(size)
    else:im=ImageOps.contain(im,size)
    scale=layer['scale'];im=im.resize((max(1,round(im.width*scale)),max(1,round(im.height*scale))))
    im=im.convert('RGBA')
    if layer['rotation']:im=im.rotate(-layer['rotation'],expand=True,resample=Image.Resampling.BICUBIC)
    im.putalpha(round(255*max(0,min(1,layer['alpha']))))
    x=round((width-im.width)/2+width*layer['x']);y=round((height-im.height)/2+height*layer['y'])
    return im,(x,y)


def draw_titles(canvas,titles):
    for title in titles:
        props=title['text'];overlay=Image.new('RGBA',canvas.size)
        fontpath=Path('C:/Windows/Fonts')/('segoeuib.ttf' if props['bold'] else 'segoeui.ttf')
        font=ImageFont.truetype(str(fontpath),max(8,round(canvas.height*props['size']*title['scale'])))
        draw=ImageDraw.Draw(overlay);text=props['text']
        box=draw.multiline_textbbox((0,0),text,font=font,align=props['align'].lower())
        x=(canvas.width-(box[2]-box[0]))/2+canvas.width*title['x'];y=(canvas.height-(box[3]-box[1]))/2+canvas.height*title['y']
        def rgba(argb,alpha=1):return ((argb>>16)&255,(argb>>8)&255,argb&255,round(((argb>>24)&255)*alpha))
        if props['boxOpacity']>0:draw.rectangle((x-12,y-8,x+box[2]+12,y+box[3]+8),fill=rgba(props['boxColorArgb'],props['boxOpacity']))
        draw.multiline_text((x,y-box[1]),text,font=font,fill=rgba(props['colorArgb']),align=props['align'].lower(),
            stroke_width=1 if props['shadow'] else 0,stroke_fill=(0,0,0,180))
        if title['rotation']:overlay=overlay.rotate(-title['rotation'],resample=Image.Resampling.BICUBIC)
        if title['alpha']!=1:overlay.putalpha(overlay.getchannel('A').point(lambda p:round(p*title['alpha'])))
        canvas.alpha_composite(overlay)


def render_project(host,snapshot,output,progress,preview=False):
    project=snapshot['project'];duration=snapshot['duration_us']
    if duration<=0:raise ValueError('El montaje está vacío.')
    media={m['id']:m for m in project['media']}
    missing=[m['displayName'] for m in media.values() if not local_path(m['uri']).is_file()]
    if missing:raise ValueError('Revincula los medios ausentes: '+', '.join(missing))
    width,height=project['settings']['width'],project['settings']['height']
    if preview:
        ratio=min(1,640/max(width,height));width=max(16,int(width*ratio)//2*2);height=max(16,int(height*ratio)//2*2)
    rate=project['settings']['frameRate'];fps=rate['num']/rate['den']
    count=math.ceil(duration*rate['num']/(rate['den']*1_000_000))
    ffmpeg=host.native.ffmpeg();decoders=OrderedDict();encoder=None
    output_tmp=output.with_suffix('.partial.mp4')
    try:
        with tempfile.TemporaryDirectory(prefix='nexus-corte-') as tmp, ExitStack() as resources:
            tmp=Path(tmp);audio={}
            for mid,m in media.items():
                if not m['hasAudio']:continue
                target=tmp/(mid+'.f32')
                subprocess.run([ffmpeg,'-v','error','-nostdin','-threads','1','-i',str(local_path(m['uri'])),'-vn','-ac','2','-ar','48000','-f','f32le',str(target)],check=True,creationflags=FLAGS,timeout=600,capture_output=True)
                if target.stat().st_size:
                    audio[mid]=np.memmap(target,dtype='<f4',mode='r').reshape(-1,2)
                    resources.callback(audio[mid]._mmap.close)
            pcm=tmp/'mix.wav';mix=wave.open(str(pcm),'wb');mix.setnchannels(2);mix.setsampwidth(2);mix.setframerate(48000)
            stderr=(tmp/'encoder.log').open('wb')
            resources.callback(stderr.close)
            encoder=subprocess.Popen([ffmpeg,'-v','error','-nostdin','-y','-threads','2','-f','rawvideo','-pix_fmt','rgb24','-s',f'{width}x{height}',
                '-r',f"{rate['num']}/{rate['den']}",'-i','pipe:0','-an','-c:v','libx264','-threads','2','-preset','veryfast','-crf','20','-pix_fmt','yuv420p',str(tmp/'picture.mp4')],stdin=subprocess.PIPE,stderr=stderr,creationflags=FLAGS)
            def release_encoder():
                if encoder.poll() is None:
                    encoder.terminate()
                    try:encoder.wait(timeout=5)
                    except subprocess.TimeoutExpired:encoder.kill();encoder.wait()
            resources.callback(release_encoder)
            try:
                for start in range(0,count,50):
                    times=[round(i*rate['den']*1_000_000/rate['num']) for i in range(start,min(count,start+50))]
                    samples=host.exchange('render_samples',{'project_id':project['id'],'expected_revision':snapshot['revision'],'render_project':project,'times':times})['samples']
                    for offset,sample in enumerate(samples):
                        i=start+offset;progress(i,count)
                        canvas=Image.new('RGBA',(width,height),(0,0,0,255))
                        for layer in sample['layers']:
                            if layer['alpha']<=0:continue
                            mid=layer['media_id']
                            if mid not in decoders:decoders[mid]=Decoder(ffmpeg,media[mid],(width,height),fps)
                            decoders.move_to_end(mid)
                            while len(decoders)>8:decoders.popitem(last=False)[1].close()
                            im,xy=layer_image(decoders[mid].get(layer['source_us']),layer,(width,height));canvas.alpha_composite(im,xy)
                        draw_titles(canvas,sample['titles']);encoder.stdin.write(canvas.convert('RGB').tobytes())
                        a=round(i*48000/fps);b=min(round((i+1)*48000/fps),round(duration*48000/1_000_000))
                        chunk=np.zeros((max(0,b-a),2),np.float32)
                        for layer in sample['audio']:
                            source=audio.get(layer['media_id'])
                            if source is None:continue
                            clip=next(c for t in project['timeline']['audioTracks'] for c in t['clips'] if c['id']==layer['clip_id'])
                            speed=clip['speed']['num']/clip['speed']['den']
                            positions=layer['source_us']*48000/1_000_000+np.arange(len(chunk))*speed
                            valid=(positions>=0)&(positions<len(source));indices=np.clip(positions.astype(np.int64),0,len(source)-1)
                            chunk+=source[indices]*valid[:,None]*layer['gain']*np.array([layer['left'],layer['right']])
                        mix.writeframes(np.int16(np.clip(chunk,-1,1)*32767).tobytes())
            finally:
                mix.close();encoder.stdin.close()
            if encoder.wait(timeout=120)!=0:
                stderr.close();raise ValueError((tmp/'encoder.log').read_text(errors='replace')[-1500:])
            stderr.close();progress(count-1,count)
            subprocess.run([ffmpeg,'-v','error','-nostdin','-y','-i',str(tmp/'picture.mp4'),'-i',str(pcm),'-c:v','copy','-c:a','aac','-b:a','192k','-t',str(duration/1_000_000),'-movflags','+faststart',str(output_tmp)],check=True,capture_output=True,timeout=120,creationflags=FLAGS)
            # Close mmap handles before Windows removes the private temporary files.
            audio.clear()
            output_tmp.replace(output)
    finally:
        for decoder in decoders.values():decoder.close()
        if encoder and encoder.poll() is None:
            encoder.terminate();encoder.wait(timeout=5)
        if output_tmp.exists():output_tmp.unlink()
