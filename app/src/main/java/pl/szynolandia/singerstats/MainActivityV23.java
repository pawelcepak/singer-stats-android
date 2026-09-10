package pl.szynolandia.singerstats;

import android.os.*;
import android.view.*;
import android.widget.*;

public class MainActivityV23 extends MainActivityV22 {
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        try{
            ViewGroup content=(ViewGroup)findViewById(android.R.id.content);
            View first=content.getChildAt(0);
            if(first instanceof LinearLayout){
                LinearLayout root=(LinearLayout)first;
                if(root.getChildCount()>0 && root.getChildAt(0) instanceof TextView)
                    ((TextView)root.getChildAt(0)).setText("Singer Stats Visualizer 2.3 — Platform Parity");
            }
        }catch(Exception ignored){}
    }
}
