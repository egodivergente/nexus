# Shared interface components

NEXUS Studio's desktop interface is vanilla HTML, CSS, and JavaScript. It has no separately exported component directory. The reusable presentation helpers used by the desktop shell are kept together in `desktop/ui/desktop-design.js`.

## `DesktopWelcome` and `DesktopIdeaButtons`

- Source: `desktop/ui/desktop-design.js`
- Category: basic
- Description: welcome state and three action cards reused by the empty creation and assistant views.
- Extractable props: `kind`, `target`.

```js
const desktopSpark='<svg viewBox="0 0 40 40" fill="none" aria-hidden="true"><path d="M20 5c1.8 9.8 5.2 13.2 15 15-9.8 1.8-13.2 5.2-15 15C18.2 25.2 14.8 21.8 5 20c9.8-1.8 13.2-5.2 15-15Z" stroke="currentColor" stroke-width="1.7"/><path d="m30 5 1.2 3.8L35 10l-3.8 1.2L30 15l-1.2-3.8L25 10l3.8-1.2L30 5Z" fill="currentColor"/></svg>';
const desktopIdeas=[
 {id:'image',title:'Una imagen',subtitle:'De una idea a algo visual',icon:'library',text:'Quiero crear una imagen. Ayúdame a preparar un buen prompt y elegir el modelo.'},
 {id:'video',title:'Un vídeo',subtitle:'Una escena con movimiento',icon:'canvas',text:'Quiero crear un vídeo. Ayúdame a definir la escena y preparar sus opciones.'},
 {id:'improve',title:'Mejorar un resultado',subtitle:'Más detalle, otro acabado',icon:'settings',text:'Quiero mejorar uno de mis resultados existentes. Ayúdame a elegirlo y revisar las opciones compatibles.'}
];
function desktopIdeaButtons(target){return '<div class="nx-ideas">'+desktopIdeas.map(idea=>'<button type="button" data-desktop-idea="'+idea.id+'" data-desktop-target="'+target+'">'+icon(idea.icon)+'<span><strong>'+idea.title+'</strong><small>'+idea.subtitle+'</small></span></button>').join('')+'</div>';}
function desktopWelcome(kind){return '<div class="nx-welcome-mark">'+desktopSpark+'</div><span class="nx-kicker">TU ESPACIO CREATIVO</span><h2>'+(kind==='assistant'?'Las buenas ideas empiezan aquí.':'Dale forma a tu próxima idea.')+'</h2><p>'+(kind==='assistant'?'Cuéntame qué tienes en mente. Lo preparamos juntos.':'Una imagen, un vídeo o un nuevo acabado. Tú eliges por dónde empezar.')+'</p>';}
```

No other desktop primitive is separately exported. Cards, controls and form fields are native elements styled by the CSS layers listed in `theme.md`.
