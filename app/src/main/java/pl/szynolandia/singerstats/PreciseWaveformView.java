package pl.szynolandia.singerstats;

import android.content.*;
import android.graphics.*;
import android.view.*;
import com.arthenica.ffmpegkit.*;
import java.io.*;
import java.util.*;

public class PreciseWaveformView extends View {
    Paint p=new Paint(3); Bitmap wave; String audioPath; double duration=1,viewStart=0,viewSpan=20,cursor=0,selA=-1,selB=-1; float downX; boolean touching=false,dragging=false;
    public PreciseWaveformView(Context c){super(c);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.NORMAL));setMinimumHeight(dp(170));}
    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    public boolean isTouching(){return touching;}
    public double getDuration(){return duration;}
    public double getViewStart(){return viewStart;}
    public double getViewSpan(){return Math.min(viewSpan,duration);}
    public double getCursor(){return cursor;}
    public void setCursor(double t){cursor=clamp(t,0,duration);if(cursor<viewStart||cursor>viewStart+viewSpan){viewStart=clamp(cursor-viewSpan*.35,0,Math.max(0,duration-viewSpan));}invalidate();}
    public boolean hasSelection(){return selA>=0&&selB>=0&&Math.abs(selB-selA)>.0005;}
    public double[] getSelection(){if(!hasSelection())return null;return new double[]{Math.min(selA,selB),Math.max(selA,selB)};}
    double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
    public void setZoom(float z){double oldCenter=viewStart+viewSpan/2;double factor=1+z*29;viewSpan=Math.max(.5,duration/factor);viewStart=clamp(oldCenter-viewSpan/2,0,Math.max(0,duration-viewSpan));invalidate();}
    public void setPan(float q){viewStart=clamp(q*Math.max(0,duration-viewSpan),0,Math.max(0,duration-viewSpan));invalidate();}
    public void loadAudio(String path,double knownDuration){audioPath=path;duration=knownDuration>0?knownDuration:1;viewSpan=Math.min(20,duration);wave=null;invalidate();
        File out=new File(getContext().getCacheDir(),"waveform_"+Math.abs(path.hashCode())+".png");
        String cmd="-y -i \""+path.replace("\"","\\\"")+"\" -filter_complex \"aformat=channel_layouts=mono,showwavespic=s=4096x320:colors=0x6F8CFF\" -frames:v 1 \""+out.getAbsolutePath()+"\"";
        FFmpegKit.executeAsync(cmd,s->{if(ReturnCode.isSuccess(s.getReturnCode())&&out.exists()){Bitmap b=BitmapFactory.decodeFile(out.getAbsolutePath());post(()->{wave=b;invalidate();});}});
    }
    double xToTime(float x){double f=clamp(x/Math.max(1,getWidth()),0,1);return clamp(viewStart+f*viewSpan,0,duration);}
    float timeToX(double t){return (float)((t-viewStart)/Math.max(.0001,viewSpan)*getWidth());}
    @Override protected void onDraw(Canvas c){super.onDraw(c);int w=getWidth(),h=getHeight();c.drawColor(Color.rgb(14,14,17));
        if(wave!=null){int sx1=(int)clamp(viewStart/duration*wave.getWidth(),0,wave.getWidth()-1);int sx2=(int)clamp((viewStart+viewSpan)/duration*wave.getWidth(),sx1+1,wave.getWidth());Rect src=new Rect(sx1,0,sx2,wave.getHeight());Rect dst=new Rect(0,25,w,h-38);p.setAlpha(210);c.drawBitmap(wave,src,dst,p);p.setAlpha(255);}else{p.setColor(Color.rgb(70,78,100));p.setStrokeWidth(2);c.drawLine(0,h/2,w,h/2,p);p.setColor(Color.LTGRAY);p.setTextSize(dp(12));c.drawText("Wczytuję waveform… (edycja czasu działa także bez podglądu amplitudy)",dp(8),h/2-dp(10),p);}
        p.setStrokeWidth(1);p.setTextSize(dp(10));for(int i=0;i<=5;i++){double t=viewStart+viewSpan*i/5.0;float x=w*i/5f;p.setColor(Color.rgb(70,70,76));c.drawLine(x,20,x,h-28,p);p.setColor(Color.LTGRAY);c.drawText(String.format(Locale.US,"%.2fs",t),x+3,h-10,p);}
        if(hasSelection()){float a=timeToX(Math.min(selA,selB)),b=timeToX(Math.max(selA,selB));p.setColor(0x406F8CFF);c.drawRect(a,20,b,h-28,p);p.setColor(Color.rgb(150,170,255));p.setStrokeWidth(2);c.drawLine(a,20,a,h-28,p);c.drawLine(b,20,b,h-28,p);}
        float cx=timeToX(cursor);p.setColor(Color.rgb(255,225,70));p.setStrokeWidth(3);c.drawLine(cx,12,cx,h-28,p);p.setTextSize(dp(11));c.drawText(String.format(Locale.US,"%.3f s",cursor),Math.max(4,Math.min(w-dp(75),cx+5)),dp(16),p);
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent e){float x=e.getX();switch(e.getActionMasked()){
        case MotionEvent.ACTION_DOWN:touching=true;dragging=false;downX=x;selA=xToTime(x);selB=selA;getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
        case MotionEvent.ACTION_MOVE:if(Math.abs(x-downX)>dp(4))dragging=true;selB=xToTime(x);cursor=selB;invalidate();return true;
        case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:touching=false;getParent().requestDisallowInterceptTouchEvent(false);if(!dragging){cursor=xToTime(x);selA=selB=-1;}else selB=xToTime(x);invalidate();return true;
    }return true;}
}
