package pl.szynolandia.singerstats;

import android.content.*;
import android.graphics.*;
import java.util.*;

public class TikTokSafeVisualizerView extends VisualizerView {
    public TikTokSafeVisualizerView(Context c){ super(c); }

    @Override protected void onDraw(Canvas c){
        super.setBackgroundColor(Color.BLACK);
        if(project==null)return;
        float s=Math.min(getWidth()/1080f,getHeight()/1920f);
        c.save();
        c.translate((getWidth()-1080*s)/2,(getHeight()-1920*s)/2);
        c.scale(s,s);
        drawSafeScene(c,project,time);
        c.restore();
    }

    public static Bitmap renderLowMemory(ProjectData p,double t){
        Bitmap b=Bitmap.createBitmap(540,960,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b); c.scale(.5f,.5f); drawSafeScene(c,p,t); return b;
    }

    static void drawSafeScene(Canvas c,ProjectData pr,double t){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); c.drawColor(Color.rgb(5,5,5));
        Bitmap cover=VisualizerView.crop(pr.coverPath,1080,1920);
        if(cover!=null){ c.drawBitmap(cover,0,0,p); cover.recycle(); p.setColor(Color.argb(125,0,0,0)); c.drawRect(0,0,1080,1920,p); }

        final float left=95f,right=895f,width=800f;
        p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.WHITE); p.setFakeBoldText(true); p.setTextSize(58); c.drawText("KTO ILE ŚPIEWA?",495,285,p);
        String title=(pr.songTitle==null||pr.songTitle.trim().isEmpty())?"Bez nazwy utworu":pr.songTitle.trim();
        p.setTextSize(44); c.drawText(title,495,342,p);
        p.setFakeBoldText(false); p.setTextSize(29); p.setColor(Color.LTGRAY); double dur=Math.max(pr.songDuration,t); c.drawText(VisualizerView.fmt(t)+" / "+VisualizerView.fmt(dur),495,382,p);

        LyricLine active=VisualizerView.activeLine(pr,t);
        if(active!=null){
            RectF box=new RectF(left,405,right,735);
            p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(165,0,0,0)); c.drawRoundRect(box,28,28,p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5);
            try{p.setColor(Color.parseColor(pr.lyricsBorderColor));}catch(Exception e){p.setColor(0xffff0050);} c.drawRoundRect(box,28,28,p); p.setStyle(Paint.Style.FILL);
            ArrayList<Singer>a=new ArrayList<>(); for(Singer s:pr.singers)if(active.singerIds.contains(s.id))a.add(s);
            float x=left+30; for(Singer s:a){VisualizerView.drawAvatar(c,p,s,x,440,72);x+=88;if(x>left+520)break;}
            StringBuilder names=new StringBuilder(); for(Singer s:a){if(names.length()>0)names.append(" + ");names.append(s.name.toUpperCase());}
            p.setTextAlign(Paint.Align.LEFT); p.setTextSize(34); p.setFakeBoldText(true);
            try{p.setColor(a.isEmpty()?Color.WHITE:Color.parseColor(a.get(0).color));}catch(Exception e){p.setColor(Color.WHITE);} c.drawText(names.toString(),left+30,565,p);
            p.setFakeBoldText(false); p.setColor(Color.WHITE); p.setTextSize(43); VisualizerView.wrap(c,p,active.text,left+30,635,width-60,47,2);
        }

        p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.WHITE); p.setTextSize(35); p.setFakeBoldText(true); c.drawText("STATYSTYKI CZASU",495,815,p); p.setFakeBoldText(false);
        LinkedHashMap<Singer,Double> scores=VisualizerView.scoreMap(pr,t); double total=0; for(double v:scores.values())total+=v;
        final float baseY=855f,rowGap=68f;
        for(Singer s:pr.singers){
            double sec=scores.get(s),pct=total>0?sec/total:0,ar=VisualizerView.animatedRank(pr,s,t); int rank=VisualizerView.hardRank(s,scores); float y=(float)(baseY+(ar-1.0)*rowGap); if(y<825||y>1365)continue;
            double frac=Math.abs(ar-Math.rint(ar)); float shift=frac>0.02f?(float)((s.id%2==0?1:-1)*28*Math.sin(Math.PI*Math.min(1.0,frac*2.0))):0;
            c.save(); c.translate(shift,0);
            VisualizerView.drawAvatar(c,p,s,left+12,y-27,48);
            p.setTextAlign(Paint.Align.LEFT); p.setTextSize(29); p.setFakeBoldText(true); p.setColor(Color.WHITE); c.drawText(rank+". "+s.name,left+76,y,p);
            p.setFakeBoldText(false); p.setTextSize(25); p.setTextAlign(Paint.Align.RIGHT); c.drawText(String.format(Locale.US,"%.1fs • %.1f%%",sec,pct*100),left+770,y,p);
            p.setColor(Color.argb(185,55,55,55)); c.drawRoundRect(left+76,y+12,left+651,y+32,10,10,p);
            try{p.setColor(Color.parseColor(s.color));}catch(Exception e){p.setColor(Color.WHITE);} c.drawRoundRect(left+76,y+12,(float)(left+76+575*pct),y+32,10,10,p);
            c.restore();
        }

        p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.rgb(245,245,245)); p.setFakeBoldText(true); p.setTextSize(27);
        boolean nearEnd=pr.songDuration>0 && t>=Math.max(0,pr.songDuration-6.0);
        String cta=nearEnd?"ZGADZASZ SIĘ Z WYNIKIEM? NAPISZ W KOMENTARZU ↓":"KOGO MAM ZROBIĆ NASTĘPNEGO? NAPISZ W KOMENTARZU ↓";
        c.drawText(cta,495,1422,p);
    }
}
