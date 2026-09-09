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
import java.util.*;

public class MainActivityV22 extends MainActivityV2 {
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        try{
            ViewGroup content=(ViewGroup)findViewById(android.R.id.content);
            View first=content.getChildAt(0);
            if(first instanceof LinearLayout){
                LinearLayout root=(LinearLayout)first;
                if(root.getChildCount()>0 && root.getChildAt(0) instanceof TextView)
                    ((TextView)root.getChildAt(0)).setText("Singer Stats Visualizer 2.2 — Platform Parity");
            }
        }catch(Exception ignored){}
    }

    @Override void projectTab(){
        super.projectTab();
        LinearLayout drive=row();
        Button da=btn("Google Drive / audio"), dc=btn("Google Drive / okładka"), dp=btn("Google Drive / projekt");
        drive.addView(da);drive.addView(dc);drive.addView(dp);body.addView(drive,2);
        body.addView(txt("Android używa systemowego wyboru plików. Jeśli masz Google Drive zalogowany w telefonie, pojawi się jako źródło bez tworzenia dodatkowego folderu synchronizacji.",13),3);
        da.setOnClickListener(v->open("audio/*",71)); dc.setOnClickListener(v->open("image/*",72)); dp.setOnClickListener(v->open("application/json",73));
    }

    @Override void renderSingers(){
        if(singerBox==null)return; singerBox.removeAllViews();
        for(Singer s:project.singers){
            LinearLayout r=row(); TextView n=txt(s.name+(s.imagePath!=null?"  [zdjęcie]":""),16);
            try{n.setTextColor(Color.parseColor(s.color));}catch(Exception ignored){}
            Button c=btn("Kolor"), im=btn(s.imagePath==null?"Zdjęcie":"Zmień"), rm=btn("Usuń foto"), del=btn("Usuń wokalistę");
            r.addView(n,new LinearLayout.LayoutParams(0,-2,1)); r.addView(c); r.addView(im); if(s.imagePath!=null)r.addView(rm); r.addView(del); singerBox.addView(r);
            c.setOnClickListener(x->colors(s.color,z->{s.color=z;renderSingers();if(viz!=null)viz.invalidate();}));
            im.setOnClickListener(x->{selectedSinger=s.id;open("image/*",12);});
            rm.setOnClickListener(x->{s.imagePath=null;renderSingers();if(viz!=null)viz.invalidate();});
            del.setOnClickListener(x->deleteSinger(s));
        }
        singerBox.addView(txt("Kolory wokalistów zapisują się razem z projektem .ssp.json.",12));
    }

    void deleteSinger(Singer singer){
        if(project.singers.size()<=1){Toast.makeText(this,"Projekt musi mieć przynajmniej jednego wokalistę",Toast.LENGTH_LONG).show();return;}
        new AlertDialog.Builder(this).setTitle("Usuń wokalistę").setMessage("Usunąć „"+singer.name+"”? Przypisania tej osoby zostaną usunięte z linijek tekstu.")
            .setPositiveButton("Usuń",(d,w)->{
                project.singers.remove(singer);
                int fallback=project.singers.get(0).id;
                for(LyricLine l:project.lyrics){l.singerIds.remove((Integer)singer.id);if(l.singerIds.isEmpty())l.singerIds.add(fallback);}
                renderSingers();timingTab();if(viz!=null)viz.invalidate();
            }).setNegativeButton("Anuluj",null).show();
    }

    @Override void lineCard(int i){
        LyricLine l=project.lyrics.get(i);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(8,8,8,12); box.setBackgroundColor(CARD);
        LinearLayout tr=row(); tr.addView(txt((i+1)+".",15)); EditText textEdit=new EditText(this); textEdit.setTextColor(FG); textEdit.setText(l.text); textEdit.setSingleLine(false); tr.addView(textEdit,new LinearLayout.LayoutParams(0,-2,1)); box.addView(tr);
        textEdit.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int before,int count){l.text=s.toString();} public void afterTextChanged(Editable e){}});
        Button who=btn("Wokalista: "+names(l)); box.addView(who); who.setOnClickListener(x->pick(i));
        LinearLayout r=row(); EditText st=num(l.start), en=num(l.end); Button bs=btn("START"), be=btn("KONIEC");
        r.addView(st,new LinearLayout.LayoutParams(0,-2,1)); r.addView(bs); r.addView(en,new LinearLayout.LayoutParams(0,-2,1)); r.addView(be); box.addView(r);
        bs.setOnClickListener(x->{l.start=now();st.setText(f(l.start));}); be.setOnClickListener(x->{l.end=now();en.setText(f(l.end));});
        st.setOnFocusChangeListener((x,z)->{if(!z)l.start=parse(st);}); en.setOnFocusChangeListener((x,z)->{if(!z)l.end=parse(en);});
        Button prev=btn("START = poprzedni KONIEC"); prev.setEnabled(i>0 && project.lyrics.get(i-1).end!=null); box.addView(prev);
        prev.setOnClickListener(x->{if(i>0&&project.lyrics.get(i-1).end!=null){l.start=project.lyrics.get(i-1).end;st.setText(f(l.start));}});
        timeline.addView(box);
    }

    @Override void publicationTab(){
        clear();
        publicationPlanner=new PublicationPlannerViewV22(this,()->project==null?"":project.songTitle);
        body.addView(publicationPlanner,new LinearLayout.LayoutParams(-1,-2));
    }

    @Override protected void onActivityResult(int req,int res,Intent data){
        if((req==71||req==72||req==73) && res==RESULT_OK && data!=null && data.getData()!=null){
            Uri u=data.getData();
            try{
                if(req==71){String p=copy(u,"audio"+ext(u));if(player!=null)try{player.release();}catch(Exception ignored){}player=new MediaPlayer();player.setDataSource(p);player.prepare();project.audioPath=p;project.songDuration=player.getDuration()/1000.0;Toast.makeText(this,"Audio wczytane",Toast.LENGTH_SHORT).show();}
                else if(req==72){project.coverPath=copy(u,"cover"+ext(u));Toast.makeText(this,"Okładka wczytana",Toast.LENGTH_SHORT).show();}
                else {project=ProjectData.from(new JSONObject(read(u)));project.audioPath=null;project.coverPath=null;for(Singer s:project.singers)s.imagePath=null;projectTab();Toast.makeText(this,"Projekt otwarty. Wskaż ponownie audio i obrazy.",Toast.LENGTH_LONG).show();}
            }catch(Exception e){new AlertDialog.Builder(this).setMessage(e.toString()).setPositiveButton("OK",null).show();}
            return;
        }
        super.onActivityResult(req,res,data);
    }
}
