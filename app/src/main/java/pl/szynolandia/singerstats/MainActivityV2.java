package pl.szynolandia.singerstats;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.net.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.regex.*;

public class MainActivityV2 extends MainActivity {
    CheckBox includeAudioExport;
    PreciseWaveformView preciseWave;
    SeekBar waveZoom, wavePan;
    Spinner waveLinePicker;
    PublicationPlannerView publicationPlanner;
    Handler parityHandler = new Handler();

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        try{
            ViewGroup content=(ViewGroup)findViewById(android.R.id.content);
            View first=content.getChildAt(0);
            if(first instanceof LinearLayout){
                LinearLayout root=(LinearLayout)first;
                if(root.getChildCount()>0 && root.getChildAt(0) instanceof TextView)
                    ((TextView)root.getChildAt(0)).setText("Singer Stats Visualizer 2.1 — Platform Parity");
                if(root.getChildCount()>1 && root.getChildAt(1) instanceof LinearLayout){
                    LinearLayout nav=(LinearLayout)root.getChildAt(1);
                    Button pub=btn("Publikacje");
                    nav.addView(pub);
                    pub.setOnClickListener(v->publicationTab());
                }
            }
        }catch(Exception ignored){}
        parityHandler.post(parityTick);
    }

    Runnable parityTick=new Runnable(){ public void run(){
        try{
            if(preciseWave!=null && player!=null){
                preciseWave.setCursor(player.getCurrentPosition()/1000.0);
                if(wavePan!=null && !preciseWave.isTouching()){
                    double max=Math.max(0,preciseWave.getDuration()-preciseWave.getViewSpan());
                    if(max>0){int p=(int)Math.round(1000.0*preciseWave.getViewStart()/max);wavePan.setProgress(Math.max(0,Math.min(1000,p)));}
                }
            }
        }catch(Exception ignored){}
        parityHandler.postDelayed(this,50);
    }};

    @Override void timingTab(){
        super.timingTab();
        try{
            int insertAt=body.indexOfChild(timeline);
            if(insertAt<0) insertAt=Math.min(2,body.getChildCount());
            LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(8,10,8,12); box.setBackgroundColor(Color.rgb(28,28,31));
            box.addView(txt("Precyzyjny waveform / edycja 1 ms",17));
            TextView hint=txt("Dotknij = ustaw kursor. Przeciągnij = zaznaczenie START–KONIEC. Suwak Zoom przybliża, suwak Przewiń przesuwa widok. To odpowiednik desktopowego edytora waveform.",13); box.addView(hint);

            preciseWave=new PreciseWaveformView(this); preciseWave.setBackgroundColor(Color.rgb(15,15,18));
            box.addView(preciseWave,new LinearLayout.LayoutParams(-1,dp(190)));
            if(project.audioPath!=null) preciseWave.loadAudio(project.audioPath,project.songDuration);

            LinearLayout zrow=row(); zrow.addView(txt("Zoom",13)); waveZoom=new SeekBar(this);waveZoom.setMax(1000);waveZoom.setProgress(220);zrow.addView(waveZoom,new LinearLayout.LayoutParams(0,-2,1));box.addView(zrow);
            waveZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){if(preciseWave!=null)preciseWave.setZoom(p/1000f);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});

            LinearLayout prow=row(); prow.addView(txt("Przewiń",13)); wavePan=new SeekBar(this);wavePan.setMax(1000);prow.addView(wavePan,new LinearLayout.LayoutParams(0,-2,1));box.addView(prow);
            wavePan.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){if(from&&preciseWave!=null)preciseWave.setPan(p/1000f);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});

            HorizontalScrollView hs=new HorizontalScrollView(this); LinearLayout fine=row();
            int[] ds={-100,-10,-1,1,10,100}; String[] labs={"-100 ms","-10 ms","-1 ms","+1 ms","+10 ms","+100 ms"};
            for(int i=0;i<ds.length;i++){final int d=ds[i];Button nb=btn(labs[i]);nb.setOnClickListener(v->nudgePrecise(d));fine.addView(nb);}hs.addView(fine);box.addView(hs);

            waveLinePicker=new Spinner(this); ArrayList<String> opts=new ArrayList<>();
            for(int i=0;i<project.lyrics.size();i++){String s=project.lyrics.get(i).text; if(s.length()>44)s=s.substring(0,44)+"…";opts.add((i+1)+". "+s);}
            ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,opts);waveLinePicker.setAdapter(ad);box.addView(waveLinePicker);

            LinearLayout b1=row(); Button setS=btn("START = kursor"), setE=btn("KONIEC = kursor");b1.addView(setS);b1.addView(setE);box.addView(b1);
            Button apply=btn("START/KONIEC = zaznaczenie waveform");box.addView(apply);
            setS.setOnClickListener(v->setWaveBoundary(true)); setE.setOnClickListener(v->setWaveBoundary(false)); apply.setOnClickListener(v->applyWaveSelection());
            body.addView(box,insertAt);
        }catch(Exception e){body.addView(txt("Waveform: "+e.getMessage(),12),0);}
    }

    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    int selectedWaveLine(){return waveLinePicker==null?-1:waveLinePicker.getSelectedItemPosition();}
    void nudgePrecise(int deltaMs){if(player==null)return;if(player.isPlaying())player.pause();seek(deltaMs);if(preciseWave!=null)preciseWave.setCursor(now());}
    void setWaveBoundary(boolean start){int i=selectedWaveLine();if(i<0||i>=project.lyrics.size()){Toast.makeText(this,"Wybierz linijkę",Toast.LENGTH_SHORT).show();return;}LyricLine l=project.lyrics.get(i);double t=preciseWave==null?now():preciseWave.getCursor();if(start)l.start=t;else l.end=t;Toast.makeText(this,(start?"START":"KONIEC")+" = "+f(t)+" s",Toast.LENGTH_SHORT).show();timingTab();}
    void applyWaveSelection(){int i=selectedWaveLine();if(i<0||i>=project.lyrics.size()){Toast.makeText(this,"Wybierz linijkę",Toast.LENGTH_SHORT).show();return;}if(preciseWave==null||!preciseWave.hasSelection()){Toast.makeText(this,"Najpierw przeciągnij po waveformie",Toast.LENGTH_SHORT).show();return;}double[] s=preciseWave.getSelection();LyricLine l=project.lyrics.get(i);l.start=s[0];l.end=s[1];Toast.makeText(this,"Ustawiono START/KONIEC",Toast.LENGTH_SHORT).show();timingTab();}

    void publicationTab(){
        clear();
        publicationPlanner=new PublicationPlannerView(this,()->project==null?"":project.songTitle);
        body.addView(publicationPlanner,new LinearLayout.LayoutParams(-1,-2));
    }

    @Override void previewTab(){
        clear();
        body.addView(txt("Podgląd na żywo 9:16 w bezpiecznej strefie TikTok. Odtwarzaj, przewijaj i popraw timingi przed eksportem.",14));
        viz=new TikTokSafeVisualizerView(this); viz.setProject(project); int w=getResources().getDisplayMetrics().widthPixels-28;
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
        body.addView(txt("Finalny MP4: 1080×1920. Ważne elementy są odsunięte od przycisków TikToka i opisu na dole.",14));

        includeAudioExport=new CheckBox(this);
        includeAudioExport.setText("Dołącz muzykę / audio do MP4");
        includeAudioExport.setTextColor(FG);
        includeAudioExport.setChecked(true);
        body.addView(includeAudioExport);
        body.addView(txt("Odznacz = MP4 bez żadnej ścieżki audio. Obraz, tekst, ranking, procenty, timingi i animacje pozostają bez zmian.",13));

        LinearLayout r=row(); Button full=btn("Eksport całości"), part=btn("Fragment OD–DO"); r.addView(full);r.addView(part);body.addView(r);
        full.setOnClickListener(x->{if(player==null){Toast.makeText(this,"Najpierw wybierz audio",Toast.LENGTH_SHORT).show();return;}if(!exporting)export(0,player.getDuration()/1000.0);});
        part.setOnClickListener(x->{if(player==null){Toast.makeText(this,"Najpierw wybierz audio",Toast.LENGTH_SHORT).show();return;}if(!exporting)fragment();});

        video=new VideoView(this); body.addView(video,new LinearLayout.LayoutParams(-1,(int)(w*16f/9)));
        Button watch=btn("▶ Odtwórz ostatni MP4"); body.addView(watch);
        watch.setOnClickListener(x->{if(lastVideo!=null){video.setVideoURI(lastVideo);MediaController mc=new MediaController(this);video.setMediaController(mc);mc.setAnchorView(video);video.start();}else Toast.makeText(this,"Najpierw wygeneruj film",Toast.LENGTH_SHORT).show();});
    }

    @Override void export(double a,double z){
        if(exporting)return;exporting=true;if(player!=null&&player.isPlaying())player.pause();
        project.songDuration=player==null?project.songDuration:player.getDuration()/1000.0;
        if(progress!=null)progress.setProgress(0);
        boolean withAudio=includeAudioExport==null || includeAudioExport.isChecked();
        Toast.makeText(this,withAudio?"Eksport z audio…":"Eksport bez audio…",Toast.LENGTH_SHORT).show();
        VideoExporter.export(this,project,a,z,24,withAudio,new VideoExporter.Listener(){
            public void progress(int p){runOnUiThread(()->{if(progress!=null)progress.setProgress(p);});}
            public void done(Uri u){runOnUiThread(()->{exporting=false;lastVideo=u;if(progress!=null)progress.setProgress(100);Toast.makeText(MainActivityV2.this,"Gotowe: MP4 zapisany w galerii",Toast.LENGTH_LONG).show();});}
            public void error(String e){runOnUiThread(()->{exporting=false;new AlertDialog.Builder(MainActivityV2.this).setTitle("Błąd eksportu").setMessage(e).setPositiveButton("OK",null).show();});}
        });
    }

    @Override void generateSentenceTimeline(){
        String raw=lyrics.getText().toString().trim(); if(raw.isEmpty()){Toast.makeText(this,"Wklej najpierw tekst",Toast.LENGTH_SHORT).show();return;}
        ArrayList<String> parts=splitSentences(raw); if(parts.isEmpty()){Toast.makeText(this,"Nie znaleziono tekstu",Toast.LENGTH_SHORT).show();return;}
        project.lyrics.clear();
        Singer current=project.singers.isEmpty()?null:project.singers.get(0);
        for(String q:parts){
            LyricLine l=new LyricLine();
            Matcher tag=Pattern.compile("^\\s*\\(([^)]+)\\)\\s*(.*)$").matcher(q);
            if(tag.matches()){
                Singer found=findSingerByName(tag.group(1).trim());
                if(found!=null) current=found;
                l.text=tag.group(2).trim();
            }else l.text=q.trim();
            if(current!=null)l.singerIds.add(current.id);
            project.lyrics.add(l);
        }
        timingTab();
    }

    void openCsvForPlanner(){open("text/*",60);}
    @Override protected void onActivityResult(int req,int res,Intent data){
        if(req==60){if(res==RESULT_OK&&data!=null&&data.getData()!=null&&publicationPlanner!=null)publicationPlanner.importCsv(data.getData());return;}
        super.onActivityResult(req,res,data);
    }

    @Override protected void onDestroy(){parityHandler.removeCallbacksAndMessages(null);super.onDestroy();}
}
