package pl.szynolandia.singerstats;

import android.content.*;
import android.graphics.*;
import android.view.*;
import java.util.*;

public class VisualizerView extends View {
    ProjectData project; double time;
    public VisualizerView(Context c){super(c);setBackgroundColor(Color.BLACK);}
    public void setProject(ProjectData p){project=p;invalidate();}
    public void setTime(double t){time=t;invalidate();}
    protected void onDraw(Canvas c){
        super.onDraw(c);if(project==null)return;
        float s=Math.min(getWidth()/1080f,getHeight()/1920f);
        c.save();c.translate((getWidth()-1080*s)/2,(getHeight()-1920*s)/2);c.scale(s,s);
        drawScene(c,project,time);c.restore();
    }
    static Bitmap crop(String path,int w,int h){
        if(path==null)return null;Bitmap src=BitmapFactory.decodeFile(path);if(src==null)return null;
        float z=Math.max(w/(float)src.getWidth(),h/(float)src.getHeight());
        Bitmap scaled=Bitmap.createScaledBitmap(src,Math.round(src.getWidth()*z),Math.round(src.getHeight()*z),true);
        int x=Math.max(0,(scaled.getWidth()-w)/2),y=Math.max(0,(scaled.getHeight()-h)/2);
        Bitmap result=Bitmap.createBitmap(scaled,x,y,Math.min(w,scaled.getWidth()-x),Math.min(h,scaled.getHeight()-y));
        if(scaled!=src)src.recycle();if(result!=scaled)scaled.recycle();return result;
    }
    static void drawAvatar(Canvas c,Paint p,Singer s,float left,float top,float size){
        RectF r=new RectF(left,top,left+size,top+size);Bitmap av=crop(s.imagePath,(int)size,(int)size);
        if(av!=null){Path path=new Path();path.addOval(r,Path.Direction.CW);c.save();c.clipPath(path);c.drawBitmap(av,null,r,p);c.restore();av.recycle();}
        else{try{p.setColor(Color.parseColor(s.color));}catch(Exception e){p.setColor(Color.GRAY);}c.drawOval(r,p);}
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setColor(Color.argb(220,245,245,245));c.drawOval(r,p);p.setStyle(Paint.Style.FILL);
    }
    public static Bitmap render(ProjectData p,double t){Bitmap b=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888);drawScene(new Canvas(b),p,t);return b;}
    public static Bitmap renderLowMemory(ProjectData p,double t){Bitmap b=Bitmap.createBitmap(540,960,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.scale(0.5f,0.5f);drawScene(c,p,t);return b;}
    static LyricLine activeLine(ProjectData pr,double t){LyricLine active=null;double latest=-1;for(LyricLine l:pr.lyrics){if(l.start!=null&&l.end!=null&&t>=l.start&&t<=l.end&&l.start>=latest){active=l;latest=l.start;}}return active;}
    static double singerTime(ProjectData pr,Singer singer,double t){double sec=0;for(LyricLine l:pr.lyrics){if(!l.singerIds.contains(singer.id)||l.start==null||l.end==null)continue;if(t>l.end)sec+=Math.max(0,l.end-l.start);else if(t>l.start)sec+=Math.max(0,t-l.start);}return sec;}
    static LinkedHashMap<Singer,Double> scoreMap(ProjectData pr,double t){LinkedHashMap<Singer,Double>m=new LinkedHashMap<>();for(Singer s:pr.singers)m.put(s,singerTime(pr,s,t));return m;}
    static int hardRank(Singer me,LinkedHashMap<Singer,Double> scores){int r=1;double mine=scores.get(me);for(Map.Entry<Singer,Double>e:scores.entrySet()){if(e.getKey()==me)continue;double other=e.getValue();if(other>mine+0.0001||(Math.abs(other-mine)<=0.0001&&e.getKey().id<me.id))r++;}return r;}
    static double smoothRank(Singer me,LinkedHashMap<Singer,Double> scores){
        double mine=scores.get(me);
        if(mine<=0.0001){return hardRank(me,scores);}
        double rank=1.0,softness=0.28;
        for(Map.Entry<Singer,Double>e:scores.entrySet()){
            if(e.getKey()==me)continue;double other=e.getValue();
            if(other<=0.0001){continue;}
            double diff=other-mine;
            if(Math.abs(diff)<=0.0001){if(e.getKey().id<me.id)rank+=1.0;continue;}
            double d=diff/softness;
            if(d>8)rank+=1.0;else if(d<-8)rank+=0.0;else rank+=1.0/(1.0+Math.exp(-d));
        }
        int zeroAhead=0;for(Map.Entry<Singer,Double>e:scores.entrySet())if(e.getKey()!=me&&e.getValue()<=0.0001&&e.getKey().id<me.id&&mine<=0.0001)zeroAhead++;
        return rank+zeroAhead;
    }
    static float sideShift(double smoothRank,int singerId){double frac=Math.abs(smoothRank-Math.rint(smoothRank));if(frac<0.03)return 0;double strength=Math.sin(Math.PI*Math.min(1.0,frac*2.0));return(float)((singerId%2==0?1:-1)*70.0*strength);}

    static void drawScene(Canvas c,ProjectData pr,double t){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);c.drawColor(Color.rgb(5,5,5));
        Bitmap cover=crop(pr.coverPath,1080,1920);if(cover!=null){c.drawBitmap(cover,0,0,p);cover.recycle();p.setColor(Color.argb(125,0,0,0));c.drawRect(0,0,1080,1920,p);}
        p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.WHITE);p.setTextSize(54);p.setFakeBoldText(true);c.drawText("KTO ILE ŚPIEWA?",540,105,p);
        String title=(pr.songTitle==null||pr.songTitle.trim().isEmpty())?"Bez nazwy utworu":pr.songTitle.trim();p.setTextSize(43);c.drawText(title,540,165,p);
        p.setTextSize(30);p.setFakeBoldText(false);p.setColor(Color.LTGRAY);double dur=Math.max(pr.songDuration,t);c.drawText(fmt(t)+" / "+fmt(dur),540,213,p);

        LyricLine active=activeLine(pr,t);
        if(active!=null){
            p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(160,0,0,0));c.drawRoundRect(75,620,1005,1015,30,30,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7);try{p.setColor(Color.parseColor(pr.lyricsBorderColor));}catch(Exception e){p.setColor(0xffff0050);}c.drawRoundRect(75,620,1005,1015,30,30,p);p.setStyle(Paint.Style.FILL);
            ArrayList<Singer>a=new ArrayList<>();for(Singer s:pr.singers)if(active.singerIds.contains(s.id))a.add(s);
            int x=120;for(Singer s:a){drawAvatar(c,p,s,x,665,92);x+=110;if(x>780)break;}
            StringBuilder names=new StringBuilder();for(Singer s:a){if(names.length()>0)names.append(" + ");names.append(s.name.toUpperCase());}
            p.setTextAlign(Paint.Align.LEFT);p.setTextSize(34);p.setFakeBoldText(true);try{p.setColor(a.isEmpty()?Color.WHITE:Color.parseColor(a.get(0).color));}catch(Exception e){p.setColor(Color.WHITE);}c.drawText(names.toString(),120,825,p);
            p.setFakeBoldText(false);p.setColor(Color.WHITE);p.setTextSize(44);wrap(c,p,active.text,120,910,830,54,2);
        }

        p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.WHITE);p.setTextSize(32);p.setFakeBoldText(true);c.drawText("STATYSTYKI CZASU",540,1145,p);p.setFakeBoldText(false);
        LinkedHashMap<Singer,Double>scores=scoreMap(pr,t);double total=0;for(double v:scores.values())total+=v;
        final float baseY=1235f,rowGap=98f;
        for(Singer s:pr.singers){
            double sec=scores.get(s),pct=total>0?sec/total:0,sr=smoothRank(s,scores);int rank=hardRank(s,scores);float y=(float)(baseY+(sr-1.0)*rowGap);if(y<1190||y>1840)continue;
            float shift=sideShift(sr,s.id);c.save();c.translate(shift,0);
            drawAvatar(c,p,s,88,y-38,62);p.setTextAlign(Paint.Align.LEFT);p.setTextSize(28);p.setColor(Color.WHITE);c.drawText(rank+". "+s.name,170,y,p);
            p.setTextAlign(Paint.Align.RIGHT);c.drawText(String.format(Locale.US,"%.1fs • %.1f%%",sec,pct*100),970,y,p);
            p.setColor(Color.argb(175,55,55,55));c.drawRoundRect(170,y+18,810,y+48,15,15,p);try{p.setColor(Color.parseColor(s.color));}catch(Exception e){p.setColor(Color.WHITE);}c.drawRoundRect(170,y+18,(float)(170+640*pct),y+48,15,15,p);c.restore();
        }
    }
    static ArrayList<Object[]>stats(ProjectData p,double t){ArrayList<Object[]>r=new ArrayList<>();for(Singer s:p.singers)r.add(new Object[]{s,singerTime(p,s,t)});r.sort((a,b)->{int cmp=Double.compare((double)b[1],(double)a[1]);if(cmp!=0)return cmp;return Integer.compare(((Singer)a[0]).id,((Singer)b[0]).id);});return r;}
    static String fmt(double sec){if(sec<0||Double.isNaN(sec)||Double.isInfinite(sec))sec=0;int m=(int)(sec/60);return String.format(Locale.US,"%d:%05.2f",m,sec-m*60);}
    static void wrap(Canvas c,Paint p,String text,float x,float y,float max,float lineH,int maxLines){String line="";int n=0;for(String w:text.split("\\s+")){String test=line.isEmpty()?w:line+" "+w;if(p.measureText(test)>max&&!line.isEmpty()){c.drawText(line,x,y+n*lineH,p);if(++n>=maxLines)return;line=w;}else line=test;}if(n<maxLines)c.drawText(line,x,y+n*lineH,p);}
}
