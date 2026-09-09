package pl.szynolandia.singerstats;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class PublicationPlannerViewV22 extends PublicationPlannerView {
    PublicationPlannerViewV22(MainActivityV2 c,SongProvider s){super(c,s);}

    @Override void build(){
        removeAllViews();TextView h=tx("PLAN PUBLIKACJI / NOTATKI",20);h.setTypeface(null,Typeface.BOLD);addView(h);
        addView(tx("Plan zapisuje się niezależnie od .ssp.json. Dodano status „Nie wchodzi” oraz sortowanie po statusie i dacie.",13));
        HorizontalScrollView hs=new HorizontalScrollView(a);LinearLayout bar=rr();String[] labs={"+ Dodaj","+ Bieżący utwór","Duplikuj","Usuń","Eksport CSV","Import CSV"};for(String s:labs){Button b=bt(s);bar.addView(b);if(s.equals("+ Dodaj"))b.setOnClickListener(v->addEntry(""));else if(s.equals("+ Bieżący utwór"))b.setOnClickListener(v->addEntry(song.get()));else if(s.equals("Duplikuj"))b.setOnClickListener(v->duplicate());else if(s.equals("Usuń"))b.setOnClickListener(v->delete());else if(s.equals("Eksport CSV"))b.setOnClickListener(v->exportCsv());else b.setOnClickListener(v->a.openCsvForPlanner());}hs.addView(bar);addView(hs);
        LinearLayout sort=rr();Spinner mode=new Spinner(a), order=new Spinner(a);String[] modes={"Bez sortowania","Status","Data"}, orders={"Rosnąco","Malejąco"};mode.setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,modes));order.setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,orders));Button go=bt("Sortuj");sort.addView(mode,new LayoutParams(0,-2,1));sort.addView(order,new LayoutParams(0,-2,1));sort.addView(go);addView(sort);go.setOnClickListener(v->sortRows((String)mode.getSelectedItem(),"Malejąco".equals(order.getSelectedItem())));
        list=new LinearLayout(a);list.setOrientation(VERTICAL);addView(list);editor=new LinearLayout(a);editor.setOrientation(VERTICAL);addView(editor);render();
    }

    void sortRows(String mode,boolean reverse){
        final Map<String,Integer> rank=new HashMap<>();String[] ss={"Pomysł","W przygotowaniu","Gotowe","Zaplanowane","Wrzucone","Nie wchodzi"};for(int i=0;i<ss.length;i++)rank.put(ss[i],i);
        Comparator<Entry> cmp;
        if("Status".equals(mode))cmp=(x,y)->{int a=rank.getOrDefault(x.status,999),b=rank.getOrDefault(y.status,999);if(a!=b)return Integer.compare(a,b);int d=x.date.compareTo(y.date);return d!=0?d:x.time.compareTo(y.time);};
        else if("Data".equals(mode))cmp=(x,y)->{int d=x.date.compareTo(y.date);if(d!=0)return d;d=x.time.compareTo(y.time);return d!=0?d:Integer.compare(rank.getOrDefault(x.status,999),rank.getOrDefault(y.status,999));};
        else return;
        Collections.sort(rows,reverse?cmp.reversed():cmp);selected=-1;save();render();
    }

    @Override void renderEditor(){
        editor.removeAllViews();if(selected<0||selected>=rows.size()){editor.addView(tx("Dodaj wpis albo wybierz istniejący.",13));return;}Entry e=rows.get(selected);
        Spinner st=new Spinner(a);String[] statuses={"Pomysł","W przygotowaniu","Gotowe","Zaplanowane","Wrzucone","Nie wchodzi"};st.setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,statuses));int si=Arrays.asList(statuses).indexOf(e.status);st.setSelection(Math.max(0,si));editor.addView(tx("Status",13));editor.addView(st);
        EditText material=ed(e.material,false),date=ed(e.date,false),time=ed(e.time,false),title=ed(e.title,false),desc=ed(e.description,true),tags=ed(e.hashtags,false),notes=ed(e.notes,true);
        editor.addView(tx("Utwór / materiał",13));editor.addView(material);LinearLayout dt=rr();dt.addView(date,new LayoutParams(0,-2,1));dt.addView(time,new LayoutParams(0,-2,1));editor.addView(tx("Data / godzina",13));editor.addView(dt);editor.addView(tx("Tytuł",13));editor.addView(title);editor.addView(tx("Opis",13));editor.addView(desc);editor.addView(tx("Hashtagi",13));editor.addView(tags);editor.addView(tx("Notatki",13));editor.addView(notes);
        Button saveBtn=bt("Zapisz zmiany");editor.addView(saveBtn);saveBtn.setOnClickListener(v->{e.status=(String)st.getSelectedItem();e.material=material.getText().toString();e.date=date.getText().toString();e.time=time.getText().toString();e.title=title.getText().toString();e.description=desc.getText().toString();e.hashtags=tags.getText().toString();e.notes=notes.getText().toString();save();render();Toast.makeText(a,"Zapisano",Toast.LENGTH_SHORT).show();});
        LinearLayout cp=rr();Button c1=bt("Kopiuj tytuł"),c2=bt("Kopiuj opis"),c3=bt("Kopiuj #"),c4=bt("Kopiuj wszystko");cp.addView(c1);cp.addView(c2);cp.addView(c3);cp.addView(c4);HorizontalScrollView ch=new HorizontalScrollView(a);ch.addView(cp);editor.addView(ch);c1.setOnClickListener(v->copy(title.getText().toString()));c2.setOnClickListener(v->copy(desc.getText().toString()));c3.setOnClickListener(v->copy(tags.getText().toString()));c4.setOnClickListener(v->copy(join(title.getText().toString(),desc.getText().toString(),tags.getText().toString())));
    }
}
