package com.axel.apkexplorer;

import android.app.*;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Native, task-oriented presentation. Engine and archive operations stay in the activity. */
final class ExplorerUi {
    interface Events {
        void navigate(String page); void choose(String pkg); void limit(int count);
        void connect(); void start(); void repeat(); void stop(); void gallery();
        void retrieve(); void export(boolean before); void save(); void github(); void resume();
    }
    private final Activity a;
    private final String page,pkg,route,resumeInfo;
    private final int limit;
    private final boolean active,connected;
    private final File archive;
    private final Events events;
    private final int bg=0xff101115, surface=0xff1b1d24, muted=0xffa2a6b5, accent=0xffc9bdff;
    ExplorerUi(Activity a,String page,String pkg,int limit,boolean active,boolean connected,File archive,String route,String resumeInfo,Events events){
        this.resumeInfo=resumeInfo;this.a=a;this.page=page;this.pkg=pkg;this.limit=limit;this.active=active;this.connected=connected;this.archive=archive;this.route=route;this.events=events;
    }
    private int dp(int n){return Math.round(a.getResources().getDisplayMetrics().density*n);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void gap(LinearLayout p,int h){p.addView(new Space(a),new LinearLayout.LayoutParams(1,dp(h)));}
    private TextView text(LinearLayout p,String value,int size,int color){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(6),0,dp(6));p.addView(t);return t;}
    private Button button(LinearLayout p,String label,boolean primary,Runnable run){
        Button b=new Button(a);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(Typeface.create("sans-serif-medium",0));b.setTextColor(primary?bg:Color.WHITE);
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x33888888),shape(primary?accent:surface,18),null));b.setPadding(dp(16),dp(10),dp(16),dp(10));b.setMinimumHeight(dp(56));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);p.addView(b,lp);b.setOnClickListener(v->run.run());return b;
    }
    private LinearLayout card(LinearLayout p){LinearLayout c=new LinearLayout(a);c.setOrientation(1);c.setPadding(dp(20),dp(16),dp(20),dp(16));c.setBackground(shape(surface,24));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(16);p.addView(c,lp);return c;}
    private String name(String p){try{return a.getPackageManager().getApplicationLabel(a.getPackageManager().getApplicationInfo(p,0)).toString();}catch(Exception e){return p;}}
    TextView render(){
        a.getWindow().setStatusBarColor(bg);a.getWindow().setNavigationBarColor(bg);a.getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout frame=new LinearLayout(a);frame.setOrientation(1);frame.setBackgroundColor(bg);a.setContentView(frame);
        frame.setOnApplyWindowInsetsListener((v,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout root=new LinearLayout(a);root.setOrientation(1);root.setPadding(dp(24),dp(16),dp(24),dp(24));scroll.addView(root);
        LinearLayout top=new LinearLayout(a);top.setGravity(Gravity.CENTER_VERTICAL);root.addView(top);
        TextView brand=new TextView(a);brand.setText("◈  EXPLORER");brand.setTextSize(13);brand.setLetterSpacing(.12f);brand.setTextColor(accent);brand.setGravity(Gravity.CENTER_VERTICAL);top.addView(brand,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView more=new TextView(a);more.setText("⋮");more.setTextSize(28);more.setTextColor(muted);more.setGravity(Gravity.CENTER);more.setContentDescription("Más opciones");top.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));more.setOnClickListener(v->menu(more));
        gap(root,24);
        text(root,page.equals("explore")?"Explora tu app":page.equals("results")?"Tus capturas":"Antes y después",32,Color.WHITE).setTypeface(Typeface.create("sans-serif-medium",0));
        text(root,page.equals("explore")?"Descubre sus pantallas sin dejar de usar tu móvil.":page.equals("results")?"Revisa lo encontrado y llévaselo a tu agente.":"Repite un recorrido para revisar los cambios de diseño.",16,muted);
        TextView status=text(root,active?"Explorando en segundo plano…":"",13,accent);
        if(page.equals("explore"))explore(root);else if(page.equals("results"))results(root);else compare(root);
        LinearLayout nav=new LinearLayout(a);nav.setPadding(dp(16),dp(8),dp(16),dp(8));frame.addView(nav);
        String[] keys={"explore","results","compare"},labels={"Explorar","Resultados","Comparar"};
        for(int i=0;i<keys.length;i++){
            final String target=keys[i];boolean selected=page.equals(target);TextView tab=new TextView(a);tab.setText(labels[i]);tab.setTextSize(14);tab.setGravity(Gravity.CENTER);tab.setTypeface(Typeface.create("sans-serif-medium",0));tab.setTextColor(selected?accent:muted);tab.setBackground(shape(selected?surface:bg,18));tab.setSelected(selected);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(56),1);lp.setMargins(dp(3),0,dp(3),0);nav.addView(tab,lp);tab.setOnClickListener(v->events.navigate(target));
        }
        return status;
    }
    private void explore(LinearLayout root){
        LinearLayout app=card(root);text(app,"APLICACIÓN",11,muted).setLetterSpacing(.12f);
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);app.addView(row,new LinearLayout.LayoutParams(-1,dp(80)));
        ImageView icon=new ImageView(a);try{icon.setImageDrawable(a.getPackageManager().getApplicationIcon(pkg));}catch(Exception e){icon.setImageResource(R.drawable.icon);}row.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=new TextView(a);title.setText(pkg.isEmpty()?"Elige una app":name(pkg));title.setTextColor(Color.WHITE);title.setTextSize(19);title.setMaxLines(2);title.setPadding(dp(16),0,dp(8),0);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=new TextView(a);arrow.setText("⌄");arrow.setTextSize(24);arrow.setTextColor(accent);row.addView(arrow);row.setContentDescription("Elegir aplicación");row.setOnClickListener(v->{if(!active)chooseApp();});
        gap(root,20);text(root,"Profundidad del recorrido",15,Color.WHITE);
        LinearLayout levels=new LinearLayout(a);root.addView(levels);int[] counts={6,12,20};String[] labels={"Rápido · 6","Normal · 12","Amplio · 20"};
        for(int i=0;i<counts.length;i++){final int count=counts[i];TextView chip=new TextView(a);chip.setText(labels[i]);chip.setTextSize(12);chip.setGravity(Gravity.CENTER);chip.setTextColor(limit==count?bg:muted);chip.setBackground(shape(limit==count?accent:surface,14));chip.setSelected(limit==count);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.setMargins(0,dp(8),dp(6),0);levels.addView(chip,lp);chip.setOnClickListener(v->{if(!active)events.limit(count);});}
        gap(root,16);
        if(connected&&!active&&resumeInfo!=null&&resumeInfo.contains("|")){
            String[] parts=resumeInfo.split("\\|",2);
            LinearLayout resume=card(root);text(resume,"AUDITORÍA INTERRUMPIDA",11,accent).setLetterSpacing(.12f);text(resume,name(parts[0]),19,Color.WHITE);text(resume,parts[1]+" · Se retoma sin repetir las pantallas ya capturadas.",13,muted);
            button(resume,"Reanudar auditoría",false,events::resume);
            gap(root,8);
        }
        Button primary=button(root,active?"Explorando…":!connected?"Preparar conexión":pkg.isEmpty()?"Elegir aplicación":"Explorar "+name(pkg),true,()->{if(!connected)events.connect();else if(pkg.isEmpty())chooseApp();else events.start();});primary.setEnabled(!active);primary.setAlpha(active?.55f:1f);
        if(active){ProgressBar bar=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);bar.setIndeterminate(true);bar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(accent));root.addView(bar,new LinearLayout.LayoutParams(-1,dp(4)));button(root,"Detener recorrido",false,events::stop);}
        text(root,!connected?"Conecta Shizuku para capturar en segundo plano.":"Conexión lista · Puedes usar otra app durante el recorrido.",13,muted);
        gap(root,22);text(root,"Al terminar",14,Color.WHITE);text(root,"Las capturas aparecerán en Resultados. Desde allí podrás revisarlas o exportarlas para una auditoría.",14,muted);
    }
    private void chooseApp(){
        List<ResolveInfo> found=a.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);found.sort(Comparator.comparing(r->r.loadLabel(a.getPackageManager()).toString(),String.CASE_INSENSITIVE_ORDER));
        Map<String,String> entries=new LinkedHashMap<>();for(ResolveInfo r:found)if(!r.activityInfo.packageName.equals(a.getPackageName()))entries.put(r.activityInfo.packageName,r.loadLabel(a.getPackageManager()).toString());
        LinearLayout box=new LinearLayout(a);box.setOrientation(1);box.setPadding(dp(20),dp(8),dp(20),dp(8));EditText query=new EditText(a);query.setSingleLine(true);query.setHint("Buscar aplicación");query.setTextSize(16);query.setTextColor(Color.WHITE);query.setHintTextColor(muted);query.setPadding(dp(14),dp(8),dp(14),dp(8));query.setBackground(shape(bg,14));box.addView(query,new LinearLayout.LayoutParams(-1,dp(56)));
        ListView list=new ListView(a);list.setDivider(null);box.addView(list,new LinearLayout.LayoutParams(-1,dp(320)));List<String> ids=new ArrayList<>(entries.keySet());ArrayAdapter<String> adapter=new ArrayAdapter<>(a,android.R.layout.simple_list_item_1,new ArrayList<>(entries.values()));list.setAdapter(adapter);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("¿Qué app quieres explorar?").setView(box).setNegativeButton("Cancelar",null).create();
        query.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int after){}public void afterTextChanged(android.text.Editable e){}public void onTextChanged(CharSequence s,int st,int before,int count){ids.clear();adapter.clear();for(Map.Entry<String,String> e:entries.entrySet())if((e.getValue()+" "+e.getKey()).toLowerCase(Locale.ROOT).contains(s.toString().toLowerCase(Locale.ROOT))){ids.add(e.getKey());adapter.add(e.getValue());}adapter.notifyDataSetChanged();}});
        list.setOnItemClickListener((parent,v,pos,id)->{dialog.dismiss();events.choose(ids.get(pos));});dialog.show();dialog.getWindow().setBackgroundDrawable(shape(surface,28));dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setAllCaps(false);
    }
    private void results(LinearLayout root){
        if(!archive.exists()){
            LinearLayout empty=card(root);gap(empty,20);text(empty,"Todavía no hay capturas",22,Color.WHITE);text(empty,active?"El recorrido está en marcha. Se guardarán aquí al terminar.":"Elige una app y empieza tu primer recorrido.",15,muted);gap(empty,20);
            button(root,active?"Ver progreso":"Explorar una app",true,()->events.navigate("explore"));if(connected&&!active)button(root,"Recuperar sesión anterior",false,events::retrieve);return;
        }
        LinearLayout result=card(root);text(result,"ÚLTIMO RECORRIDO",11,accent).setLetterSpacing(.12f);TextView summary=text(result,"Capturas guardadas",21,Color.WHITE);LinearLayout previews=new LinearLayout(a);result.addView(previews);text(result,"Toca para abrir la galería",12,muted);result.setOnClickListener(v->events.gallery());
        new Thread(()->{try(ZipFile zip=new ZipFile(archive)){
            JSONObject report;try(InputStream in=zip.getInputStream(zip.getEntry("agent.json"))){report=new JSONObject(new String(in.readNBytes(1000000),java.nio.charset.StandardCharsets.UTF_8));}
            JSONArray screens=report.getJSONArray("states");String title=screens.length()+" capturas · "+name(report.getString("package"));List<Bitmap> thumbs=new ArrayList<>();
            for(int i=0;i<Math.min(3,screens.length());i++)try(InputStream in=zip.getInputStream(zip.getEntry(screens.getJSONObject(i).getString("image")))){BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;Bitmap bitmap=BitmapFactory.decodeStream(in,null,options);if(bitmap!=null)thumbs.add(bitmap);}
            a.runOnUiThread(()->{if(a.isDestroyed())return;summary.setText(title);for(Bitmap bitmap:thumbs){ImageView view=new ImageView(a);view.setImageBitmap(bitmap);view.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(190),1);lp.setMargins(dp(3),dp(16),dp(3),dp(8));previews.addView(view,lp);}});
        }catch(Exception e){a.runOnUiThread(()->summary.setText("No se pudo leer el último recorrido"));}},"preview").start();
        button(root,"Ver capturas",true,events::gallery);button(root,"Exportar para el agente",false,()->events.export(false));button(root,"Usar como referencia de diseño",false,events::save);
        text(root,"Cobertura parcial · El ZIP incluye imágenes, elementos y recorrido para revisar el diseño con el código.",13,muted);
    }
    private void compare(LinearLayout root){
        if(route.isEmpty()){LinearLayout empty=card(root);text(empty,"Empieza con una referencia",22,Color.WHITE);text(empty,"Guarda un recorrido desde Resultados. Después podrás repetirlo para revisar una nueva versión de tu app.",15,muted);button(root,"Ir a Resultados",true,()->events.navigate("results"));return;}
        String target="";try{target=new JSONObject(route).getString("package");}catch(Exception ignored){}
        LinearLayout reference=card(root);text(reference,"REFERENCIA GUARDADA",11,accent).setLetterSpacing(.12f);text(reference,name(target),23,Color.WHITE);text(reference,"1. Actualiza el diseño de esta app.\n2. Repite el mismo recorrido.\n3. Entrega ambos ZIP a tu agente.",15,muted);
        Button repeat=button(root,connected?"Repetir recorrido":"Preparar conexión",true,()->{if(connected)events.repeat();else events.connect();});repeat.setEnabled(!active);
        button(root,"Exportar referencia · Antes",false,()->events.export(true));button(root,"Ver último resultado",false,()->events.navigate("results"));text(root,"La comparación visual la realiza el agente con ambos ZIP. No se restauran datos ni contenido remoto.",13,muted);
    }
    private void menu(View anchor){PopupMenu popup=new PopupMenu(a,anchor);if(connected&&!active)popup.getMenu().add("Recuperar última sesión").setOnMenuItemClickListener(item->{events.retrieve();return true;});popup.getMenu().add("Repositorio de GitHub").setOnMenuItemClickListener(item->{events.github();return true;});popup.getMenu().add("Cómo funciona").setOnMenuItemClickListener(item->{new AlertDialog.Builder(a).setTitle("De tu app a una auditoría").setMessage("Explorar: elige una app y conecta Shizuku. Puedes usar otra app mientras trabaja.\n\nResultados: revisa y exporta las capturas para tu agente.\n\nComparar: guarda una referencia y repítela después de cambiar el diseño.\n\nLa cobertura es parcial. Los datos se comparten con la app instalada.\n\nAPK Explorer "+BuildConfig.VERSION_NAME).setPositiveButton("Entendido",null).show();return true;});popup.show();}
}
