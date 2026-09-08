package pl.szynolandia.singerstats;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.net.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    ProjectData project = new ProjectData();
    MediaPlayer player;
    Handler h = new Handler();
    LinearLayout body, timeline, singerBox;
    TextView time, autoStatus;
    EditText lyrics, songTitleInput;
    VisualizerView viz;
    ProgressBar progress, autoProgress;
    SeekBar previewSeek;
    VideoView video;
    Uri lastVideo;
    int selectedSinger = -1;
    boolean exporting = false, previewSeeking = false, autoTimingRunning = false;
    final int BG=Color.rgb(18,18,18), FG=Color.rgb(235,235,235), CARD=Color.rgb(38,38,38);

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        TextView title=txt("Singer Stats Visualizer 1.7",22); root.addView(title);
        LinearLayout nav=row(); Button a=btn("Projekt"), t=btn("Timing"), v=btn("Podgląd / eksport");
        nav.addView(a); nav.addView(t); nav.addView(v); root.addView(nav);
        ScrollView s=new ScrollView(this); body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(14,10,14,25);
        s.addView(body); root.addView(s,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        a.setOnClickListener(x->projectTab()); t.setOnClickListener(x->timingTab()); v.setOnClickListener(x->previewTab());
        projectTab(); h.post(tick);
    }

    Runnable tick=new Runnable(){ public void run(){
        try{
            if(player!=null){
                project.songDuration=player.getDuration()/1000.0;
                if(time!=null) time.setText(VisualizerView.fmt(player.getCurrentPosition()/1000.0)+" / "+VisualizerView.fmt(project.songDuration));
                if(previewSeek!=null && !previewSeeking){
                    previewSeek.setMax(Math.max(1,player.getDuration()));
                    previewSeek.setProgress(player.getCurrentPosition());
                }
            }
            if(viz!=null) viz.setTime(player==null?0:player.getCurrentPosition()/1000.0);
        }catch(Exception ignored){}
        h.postDelayed(this,50);
    }};

    TextView txt(String s,int z){ TextView x=new TextView(this); x.setText(s); x.setTextColor(FG); x.setTextSize(z); x.setPadding(8,8,8,8); return x; }
    Button btn(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); return b; }
    LinearLayout row(){ LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); return r; }
    void clear(){ body.removeAllViews(); viz=null; previewSeek=null; autoStatus=null; autoProgress=null; }

    void projectTab(){
        clear();
        LinearLayout r=row(); Button op=btn("Otwórz .ssp.json"), sv=btn("Zapisz .ssp.json"); r.addView(op); r.addView(sv); body.addView(r);
        op.setOnClickListener(x->open("application/json",30)); sv.setOnClickListener(x->create("application/json","SingerStats.ssp.json",31));
        Button audio=btn("Wybierz audio"); body.addView(audio); audio.setOnClickListener(x->open("audio/*",10));
        time=txt("0:00.00",16); body.addView(time);

        body.addView(txt("Nazwa utworu",16));
        songTitleInput=new EditText(this); songTitleInput.setTextColor(FG); songTitleInput.setSingleLine(true); songTitleInput.setHint("np. NEWGEN – Stuku Puku");
        songTitleInput.setHintTextColor(Color.GRAY); songTitleInput.setText(project.songTitle==null?"":project.songTitle); body.addView(songTitleInput);
        songTitleInput.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int before,int count){project.songTitle=s.toString();} public void afterTextChanged(Editable e){} });

        LinearLayout vr=row(); Button cover=btn("Okładka/tło"), border=btn("Kolor ramki tekstu"); vr.addView(cover); vr.addView(border); body.addView(vr);
        cover.setOnClickListener(x->open("image/*",11)); border.setOnClickListener(x->colors(project.lyricsBorderColor,c->project.lyricsBorderColor=c));

        body.addView(txt("Wokaliści",18)); singerBox=new LinearLayout(this); singerBox.setOrientation(LinearLayout.VERTICAL); body.addView(singerBox); renderSingers();
        Button add=btn("+ Dodaj wokalistę"); body.addView(add); add.setOnClickListener(x->addSinger());

        body.addView(txt("Tekst piosenki",18));
        body.addView(txt("Kropka, ! lub ? kończy fragment. Jeśli fragment zaczyna się np. (Mortal), aplikacja spróbuje od razu przypisać wokalistę o tej nazwie.",14));
        lyrics=new EditText(this); lyrics.setTextColor(FG); lyrics.setMinLines(8);
        StringBuilder sb=new StringBuilder(); for(LyricLine l:project.lyrics) sb.append(l.text).append("\n"); lyrics.setText(sb.toString()); body.addView(lyrics);
        Button gen=btn("Generuj fragmenty: kropka → kropka"); body.addView(gen); gen.setOnClickListener(x->generateSentenceTimeline());
    }

    void generateSentenceTimeline(){
        String raw=lyrics.getText().toString().trim(); if(raw.isEmpty()){Toast.makeText(this,"Wklej najpierw tekst",Toast.LENGTH_SHORT).show();return;}
        ArrayList<String> parts=splitSentences(raw); if(parts.isEmpty()){Toast.makeText(this,"Nie znaleziono tekstu",Toast.LENGTH_SHORT).show();return;}
        project.lyrics.clear();
        for(String q:parts){
            LyricLine l=new LyricLine();
            Matcher tag=Pattern.compile("^\\s*\\(([^)]+)\\)\\s*(.*)$").matcher(q);
            String singerName=null;
            if(tag.matches()){ singerName=tag.group(1).trim(); l.text=tag.group(2).trim(); } else l.text=q.trim();
            Singer found=findSingerByName(singerName);
            l.singerIds.add(found!=null?found.id:project.singers.get(0).id);
            project.lyrics.add(l);
        }
        timingTab();
    }

    Singer findSingerByName(String name){
        if(name==null) return null;
        for(Singer s:project.singers) if(s.name.trim().equalsIgnoreCase(name.trim())) return s;
        return null;
    }

    ArrayList<String> splitSentences(String raw){
        ArrayList<String> out=new ArrayList<>(); String clean=raw.replace('\r',' ').replace('\n',' ');
        Matcher m=Pattern.compile("[^.!?]+[.!?]+|[^.!?]+$").matcher(clean);
        while(m.find()){ String s=m.group().replaceAll("\\s+"," ").trim(); if(!s.isEmpty()) out.add(s); }
        return out;
    }

    void renderSingers(){
        if(singerBox==null)return; singerBox.removeAllViews();
        for(Singer s:project.singers){
            LinearLayout r=row(); TextView n=txt(s.name+(s.imagePath!=null?"  [zdjęcie]":""),16);
            try{n.setTextColor(Color.parseColor(s.color));}catch(Exception ignored){}
            Button c=btn("Kolor"), im=btn(s.imagePath==null?"Zdjęcie":"Zmień"), rm=btn("Usuń foto");
            r.addView(n,new LinearLayout.LayoutParams(0,-2,1)); r.addView(c); r.addView(im); if(s.imagePath!=null)r.addView(rm); singerBox.addView(r);
            c.setOnClickListener(x->colors(s.color,z->{s.color=z;renderSingers();if(viz!=null)viz.invalidate();}));
            im.setOnClickListener(x->{selectedSinger=s.id;open("image/*",12);});
            rm.setOnClickListener(x->{s.imagePath=null;renderSingers();if(viz!=null)viz.invalidate();});
        }
    }

    void addSinger(){
        EditText e=new EditText(this);
        new AlertDialog.Builder(this).setTitle("Nowy wokalista").setView(e).setPositiveButton("Dodaj",(d,w)->{
            String n=e.getText().toString().trim(); if(!n.isEmpty()){project.singers.add(new Singer(project.nextSingerId++,n,"#8a5cff"));renderSingers();}
        }).setNegativeButton("Anuluj",null).show();
    }

    void timingTab(){
        clear();
        time=txt("0:00.00",18); body.addView(time);
        LinearLayout r=row(); Button b=btn("-5s"), p=btn("▶ / ⏸"), f=btn("+5s"); r.addView(b); r.addView(p); r.addView(f); body.addView(r);
        b.setOnClickListener(x->seek(-5000)); p.setOnClickListener(x->play()); f.setOnClickListener(x->seek(5000));

        Button auto=btn("✨ Auto timing AI (beta)"); body.addView(auto);
        autoStatus=txt("Dopasuje wpisane fragmenty do śpiewu. Pierwsze użycie pobiera model ok. 142 MB; później analiza działa offline.",14); body.addView(autoStatus);
        autoProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); autoProgress.setMax(100); autoProgress.setProgress(0); body.addView(autoProgress);
        auto.setOnClickListener(x->startAutoTiming());

        body.addView(txt("Kalibracja po Auto Timing: jeśli wszystko jest np. 4–5 s za późno, 1 punkt przesunie cały utwór. Jeśli błąd zmienia się z czasem, użyj 2 punktów.",14));
        LinearLayout cal=row(); Button c1=btn("Kalibracja 1 punkt"), c2=btn("Kalibracja 2 punkty"); cal.addView(c1); cal.addView(c2); body.addView(cal);
        c1.setOnClickListener(x->showOnePointCalibration()); c2.setOnClickListener(x->showTwoPointCalibration());

        body.addView(txt("Po automatycznym dopasowaniu możesz ręcznie poprawić START/KONIEC. Dokładność przy śpiewie zależy od tego, jak dobrze model rozpozna wokal w miksie.",14));
        timeline=new LinearLayout(this); timeline.setOrientation(LinearLayout.VERTICAL); body.addView(timeline);
        for(int i=0;i<project.lyrics.size();i++) lineCard(i);
    }

    void startAutoTiming(){
        if(autoTimingRunning) return;
        if(player==null || project.audioPath==null){Toast.makeText(this,"Najpierw wybierz audio",Toast.LENGTH_SHORT).show();return;}
        if(project.lyrics.isEmpty()){Toast.makeText(this,"Najpierw wygeneruj fragmenty tekstu",Toast.LENGTH_SHORT).show();return;}
        if(player.isPlaying()) player.pause();
        autoTimingRunning=true; autoProgress.setProgress(0); autoStatus.setText("Uruchamiam analizę…");
        AutoTiming.run(this,project,new AutoTiming.Listener(){
            public void onStatus(String m){ if(autoStatus!=null)autoStatus.setText(m); }
            public void onProgress(int p){ if(autoProgress!=null)autoProgress.setProgress(p); }
            public void onDone(double confidence){
                autoTimingRunning=false;
                Toast.makeText(MainActivity.this,"Auto timing gotowy. Pewność dopasowania: "+String.format(Locale.US,"%.0f%%",confidence*100)+" — sprawdź podgląd.",Toast.LENGTH_LONG).show();
                timingTab();
            }
            public void onError(String m){
                autoTimingRunning=false;
                new AlertDialog.Builder(MainActivity.this).setTitle("Auto timing").setMessage(m).setPositiveButton("OK",null).show();
            }
        });
    }

    void showOnePointCalibration(){
        if(project.lyrics.isEmpty()){Toast.makeText(this,"Brak fragmentów",Toast.LENGTH_SHORT).show();return;}
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(24,8,24,8);
        EditText line=num(1d), actual=num(project.lyrics.get(0).start==null?0d:project.lyrics.get(0).start);
        box.addView(txt("Numer fragmentu",14)); box.addView(line); box.addView(txt("Prawidłowy START tej linijki (s)",14)); box.addView(actual);
        new AlertDialog.Builder(this).setTitle("Kalibracja 1 punkt").setView(box).setPositiveButton("Przelicz",(d,w)->{
            Double ln=parse(line), real=parse(actual); if(ln==null||real==null){Toast.makeText(this,"Nieprawidłowe dane",Toast.LENGTH_SHORT).show();return;}
            int idx=(int)Math.round(ln)-1; if(idx<0||idx>=project.lyrics.size()||project.lyrics.get(idx).start==null){Toast.makeText(this,"Ta linia nie ma czasu START",Toast.LENGTH_LONG).show();return;}
            double old=project.lyrics.get(idx).start; double off=real-old; applyOffset(off);
            Toast.makeText(this,"Przesunięto wszystkie czasy o "+String.format(Locale.US,"%+.3f s",off),Toast.LENGTH_LONG).show(); timingTab();
        }).setNegativeButton("Anuluj",null).show();
    }

    void showTwoPointCalibration(){
        if(project.lyrics.size()<2){Toast.makeText(this,"Potrzeba co najmniej 2 fragmentów",Toast.LENGTH_SHORT).show();return;}
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(24,8,24,8);
        EditText line1=num(1d), real1=num(project.lyrics.get(0).start==null?0d:project.lyrics.get(0).start);
        EditText line2=num((double)project.lyrics.size()), real2=num(project.lyrics.get(project.lyrics.size()-1).start==null?0d:project.lyrics.get(project.lyrics.size()-1).start);
        box.addView(txt("Punkt 1 — numer fragmentu",14)); box.addView(line1); box.addView(txt("Prawidłowy START punktu 1 (s)",14)); box.addView(real1);
        box.addView(txt("Punkt 2 — numer fragmentu",14)); box.addView(line2); box.addView(txt("Prawidłowy START punktu 2 (s)",14)); box.addView(real2);
        new AlertDialog.Builder(this).setTitle("Kalibracja 2 punkty").setView(box).setPositiveButton("Przelicz",(d,w)->{
            Double a=parse(line1), ra=parse(real1), b=parse(line2), rb=parse(real2); if(a==null||ra==null||b==null||rb==null){Toast.makeText(this,"Nieprawidłowe dane",Toast.LENGTH_SHORT).show();return;}
            int i1=(int)Math.round(a)-1, i2=(int)Math.round(b)-1;
            if(i1<0||i2<0||i1>=project.lyrics.size()||i2>=project.lyrics.size()||i1==i2){Toast.makeText(this,"Wybierz dwa różne poprawne numery fragmentów",Toast.LENGTH_LONG).show();return;}
            Double old1=project.lyrics.get(i1).start, old2=project.lyrics.get(i2).start; if(old1==null||old2==null||Math.abs(old2-old1)<0.01){Toast.makeText(this,"Wybrane linie nie mają prawidłowych czasów START",Toast.LENGTH_LONG).show();return;}
            double scale=(rb-ra)/(old2-old1); if(scale<0.90||scale>1.10){Toast.makeText(this,"Korekta byłaby zbyt duża. Sprawdź numery linii i czasy.",Toast.LENGTH_LONG).show();return;}
            double shift=ra-scale*old1; applyAffine(scale,shift);
            Toast.makeText(this,"Kalibracja zastosowana: skala "+String.format(Locale.US,"%.5f",scale)+", przesunięcie "+String.format(Locale.US,"%+.3f s",shift),Toast.LENGTH_LONG).show(); timingTab();
        }).setNegativeButton("Anuluj",null).show();
    }

    void applyOffset(double offset){
        for(LyricLine l:project.lyrics){ if(l.start!=null)l.start=Math.max(0,l.start+offset); if(l.end!=null)l.end=Math.max(0,l.end+offset); }
    }

    void applyAffine(double scale,double shift){
        for(LyricLine l:project.lyrics){ if(l.start!=null)l.start=Math.max(0,l.start*scale+shift); if(l.end!=null)l.end=Math.max(0,l.end*scale+shift); }
    }

    void lineCard(int i){
        LyricLine l=project.lyrics.get(i);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(8,8,8,12); box.setBackgroundColor(CARD);
        box.addView(txt((i+1)+". "+l.text,15));
        Button who=btn("Wokalista: "+names(l)); box.addView(who); who.setOnClickListener(x->pick(i));
        LinearLayout r=row(); EditText st=num(l.start), en=num(l.end); Button bs=btn("START"), be=btn("KONIEC");
        r.addView(st,new LinearLayout.LayoutParams(0,-2,1)); r.addView(bs); r.addView(en,new LinearLayout.LayoutParams(0,-2,1)); r.addView(be); box.addView(r);
        bs.setOnClickListener(x->{l.start=now();st.setText(f(l.start));}); be.setOnClickListener(x->{l.end=now();en.setText(f(l.end));});
        st.setOnFocusChangeListener((x,z)->{if(!z)l.start=parse(st);}); en.setOnFocusChangeListener((x,z)->{if(!z)l.end=parse(en);});
        timeline.addView(box);
    }

    EditText num(Double d){EditText e=new EditText(this);e.setTextColor(FG);e.setInputType(2|8192);if(d!=null)e.setText(f(d));return e;}
    Double parse(EditText e){try{return Double.parseDouble(e.getText().toString().replace(',','.'));}catch(Exception x){return null;}}
    String f(Double d){return d==null?"":String.format(Locale.US,"%.3f",d);}
    double now(){return player==null?0:player.getCurrentPosition()/1000.0;}

    void pick(int idx){
        LyricLine l=project.lyrics.get(idx); String[] ns=new String[project.singers.size()]; boolean[] ck=new boolean[ns.length];
        for(int i=0;i<ns.length;i++){ns[i]=project.singers.get(i).name;ck[i]=l.singerIds.contains(project.singers.get(i).id);}
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Kto śpiewa ten fragment?").setMultiChoiceItems(ns,ck,(d,which,isChecked)->ck[which]=isChecked).setPositiveButton("Zapisz",null).setNegativeButton("Anuluj",null).create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            ArrayList<Integer> ids=new ArrayList<>(); for(int i=0;i<ck.length;i++)if(ck[i])ids.add(project.singers.get(i).id);
            if(ids.isEmpty()){Toast.makeText(this,"Wybierz przynajmniej jednego wokalistę",Toast.LENGTH_SHORT).show();return;}
            l.singerIds.clear();l.singerIds.addAll(ids);dlg.dismiss();timingTab();
        })); dlg.show();
    }

    String names(LyricLine l){
        StringBuilder b=new StringBuilder(); for(Singer s:project.singers)if(l.singerIds.contains(s.id)){if(b.length()>0)b.append(" + ");b.append(s.name);}
        return b.length()==0?"brak":b.toString();
    }

    void previewTab(){
        clear();
        body.addView(txt("Podgląd na żywo — nic nie renderuje. Odtwarzaj, przewijaj i popraw timingi zanim uruchomisz eksport.",14));
        viz=new VisualizerView(this); viz.setProject(project); int w=getResources().getDisplayMetrics().widthPixels-28;
        body.addView(viz,new LinearLayout.LayoutParams(-1,(int)(w*16f/9)));

        previewSeek=new SeekBar(this); previewSeek.setMax(player==null?1:Math.max(1,player.getDuration())); body.addView(previewSeek);
        previewSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean from){ if(from&&player!=null){player.seekTo(p);if(viz!=null)viz.setTime(p/1000.0);} }
            public void onStartTrackingTouch(SeekBar s){previewSeeking=true;}
            public void onStopTrackingTouch(SeekBar s){previewSeeking=false;if(player!=null)player.seekTo(s.getProgress());}
        });
        LinearLayout transport=row(); Button back=btn("-5s"), play=btn("▶ / ⏸"), fw=btn("+5s");
        transport.addView(back);transport.addView(play);transport.addView(fw);body.addView(transport);
        back.setOnClickListener(x->seek(-5000)); play.setOnClickListener(x->play()); fw.setOnClickListener(x->seek(5000));

        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setProgress(0); body.addView(progress);
        body.addView(txt("Eksport dopiero po sprawdzeniu podglądu. Finalny MP4: 1080×1920 (9:16 TikTok).",14));
        LinearLayout r=row(); Button full=btn("Eksport całości"), part=btn("Fragment OD–DO"); r.addView(full);r.addView(part);body.addView(r);
        full.setOnClickListener(x->{if(player==null){Toast.makeText(this,"Najpierw wybierz audio",Toast.LENGTH_SHORT).show();return;}if(!exporting)export(0,player.getDuration()/1000.0);});
        part.setOnClickListener(x->{if(player==null){Toast.makeText(this,"Najpierw wybierz audio",Toast.LENGTH_SHORT).show();return;}if(!exporting)fragment();});

        video=new VideoView(this); body.addView(video,new LinearLayout.LayoutParams(-1,(int)(w*16f/9)));
        Button watch=btn("▶ Odtwórz ostatni MP4"); body.addView(watch);
        watch.setOnClickListener(x->{if(lastVideo!=null){video.setVideoURI(lastVideo);MediaController mc=new MediaController(this);video.setMediaController(mc);mc.setAnchorView(video);video.start();}else Toast.makeText(this,"Najpierw wygeneruj film",Toast.LENGTH_SHORT).show();});
    }

    void fragment(){
        LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);EditText a=num(0d),z=num(player==null?0d:player.getDuration()/1000.0);
        b.addView(txt("OD (sekundy)",14));b.addView(a);b.addView(txt("DO (sekundy)",14));b.addView(z);
        new AlertDialog.Builder(this).setTitle("Fragment OD–DO").setView(b).setPositiveButton("Generuj",(d,w)->{
            Double x=parse(a),y=parse(z);if(x!=null&&y!=null&&y>x)export(x,y);else Toast.makeText(this,"Nieprawidłowy zakres",Toast.LENGTH_SHORT).show();
        }).setNegativeButton("Anuluj",null).show();
    }

    void export(double a,double z){
        if(exporting)return;exporting=true;if(player!=null&&player.isPlaying())player.pause();
        project.songDuration=player==null?project.songDuration:player.getDuration()/1000.0;
        if(progress!=null)progress.setProgress(0);
        Toast.makeText(this,"Rozpoczynam eksport…",Toast.LENGTH_SHORT).show();
        VideoExporter.export(this,project,a,z,24,new VideoExporter.Listener(){
            public void progress(int p){runOnUiThread(()->{if(progress!=null)progress.setProgress(p);});}
            public void done(Uri u){runOnUiThread(()->{exporting=false;lastVideo=u;if(progress!=null)progress.setProgress(100);Toast.makeText(MainActivity.this,"Gotowe: MP4 zapisany w galerii",Toast.LENGTH_LONG).show();});}
            public void error(String e){runOnUiThread(()->{exporting=false;new AlertDialog.Builder(MainActivity.this).setTitle("Błąd eksportu").setMessage(e).setPositiveButton("OK",null).show();});}
        });
    }

    void play(){if(player!=null){if(player.isPlaying())player.pause();else player.start();}}
    void seek(int x){if(player!=null)player.seekTo(Math.max(0,Math.min(player.getDuration(),player.getCurrentPosition()+x)));}

    interface CR{void done(String c);}
    void colors(String cur,CR cb){
        int initial;try{initial=Color.parseColor(cur);}catch(Exception e){initial=Color.WHITE;}
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(28,12,28,4);
        TextView preview=txt("        ",20);preview.setBackgroundColor(initial);box.addView(preview,new LinearLayout.LayoutParams(-1,90));
        TextView hex=txt(String.format(Locale.US,"#%06X",(0xFFFFFF&initial)),18);box.addView(hex);
        int[]vals={Color.red(initial),Color.green(initial),Color.blue(initial)};String[]labs={"R","G","B"};
        for(int i=0;i<3;i++){TextView l=txt(labs[i]+": "+vals[i],15);box.addView(l);SeekBar s=new SeekBar(this);s.setMax(255);s.setProgress(vals[i]);final int k=i;
            s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar q,int value,boolean fromUser){vals[k]=value;l.setText(labs[k]+": "+value);int color=Color.rgb(vals[0],vals[1],vals[2]);preview.setBackgroundColor(color);hex.setText(String.format(Locale.US,"#%06X",(0xFFFFFF&color)));}public void onStartTrackingTouch(SeekBar q){}public void onStopTrackingTouch(SeekBar q){}});
            box.addView(s);
        }
        new AlertDialog.Builder(this).setTitle("Dowolny kolor RGB").setView(box).setPositiveButton("Ustaw",(d,w)->cb.done(String.format(Locale.US,"#%02X%02X%02X",vals[0],vals[1],vals[2]))).setNegativeButton("Anuluj",null).show();
    }

    void open(String mime,int code){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType(mime);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,code);}
    void create(String mime,String name,int code){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType(mime);i.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,code);}

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();
        try{
            if(req==10){
                String p=copy(u,"audio"+ext(u));if(player!=null)try{player.release();}catch(Exception ignored){}
                player=new MediaPlayer();player.setDataSource(p);player.prepare();project.audioPath=p;project.songDuration=player.getDuration()/1000.0;Toast.makeText(this,"Audio wczytane",Toast.LENGTH_SHORT).show();
            }else if(req==11){project.coverPath=copy(u,"cover"+ext(u));}
            else if(req==12){for(Singer s:project.singers)if(s.id==selectedSinger)s.imagePath=copy(u,"singer_"+s.id+ext(u));renderSingers();}
            else if(req==30){project=ProjectData.from(new JSONObject(read(u)));project.audioPath=null;project.coverPath=null;for(Singer s:project.singers)s.imagePath=null;projectTab();Toast.makeText(this,"Projekt otwarty. Wskaż ponownie audio i obrazy.",Toast.LENGTH_LONG).show();}
            else if(req==31){write(u,project.json().toString(2));}
        }catch(Exception e){new AlertDialog.Builder(this).setMessage(e.toString()).setPositiveButton("OK",null).show();}
    }

    String copy(Uri u,String n)throws Exception{
        File d=new File(getFilesDir(),"media");d.mkdirs();File f=new File(d,n);
        try(InputStream in=getContentResolver().openInputStream(u);OutputStream out=new FileOutputStream(f)){byte[]b=new byte[65536];int k;while((k=in.read(b))>0)out.write(b,0,k);}
        return f.getAbsolutePath();
    }
    String ext(Uri u){
        String t=getContentResolver().getType(u);if(t==null)return".bin";
        if(t.contains("mpeg"))return".mp3";if(t.contains("wav"))return".wav";if(t.contains("mp4")||t.contains("m4a"))return".m4a";if(t.contains("png"))return".png";if(t.contains("webp"))return".webp";if(t.contains("jpeg"))return".jpg";return".bin";
    }
    String read(Uri u)throws Exception{try(InputStream in=getContentResolver().openInputStream(u)){ByteArrayOutputStream o=new ByteArrayOutputStream();byte[]b=new byte[65536];int n;while((n=in.read(b))>0)o.write(b,0,n);return o.toString("UTF-8");}}
    void write(Uri u,String s)throws Exception{try(OutputStream o=getContentResolver().openOutputStream(u)){o.write(s.getBytes("UTF-8"));}}

    @Override protected void onDestroy(){super.onDestroy();h.removeCallbacksAndMessages(null);if(player!=null)try{player.release();}catch(Exception ignored){}}
}
