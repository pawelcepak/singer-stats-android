package pl.szynolandia.singerstats;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.media.*;
import android.net.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.regex.*;

public class MainActivityV2 extends MainActivity {
    CheckBox includeAudioExport;

    @Override public void onCreate(Bundle b){
        super.onCreate(null);
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
}
