package dev.local.ncmfurigana;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Uses the real dictionary and APK resource lookup, not a mocked tokenizer. */
public final class ReadingIntegrationTest {
    static void require(boolean value,String message) { if (!value) throw new AssertionError(message); }
    static void expect(String surface,String reading,int start,List<ReadingEngine.Ruby> rubies,String original) {
        for (ReadingEngine.Ruby ruby:rubies)
            if (ruby.start==start && original.substring(ruby.start,ruby.end).equals(surface) && ruby.reading.equals(reading)) return;
        throw new AssertionError("Missing "+surface+"→"+reading+" at "+start+" in "+original);
    }
    public static void main(String[] args) throws Exception {
        long before=System.nanoTime();
        require(ReadingEngine.class.getClassLoader().getResource("com/atilika/kuromoji/ipadic/connectionCosts.bin").toString().contains(".apk!"),"Dictionary must load from final APK");
        ReadingEngine engine=new ReadingEngine();
        String text="お寿司が食べたい。";
        List<ReadingEngine.Ruby> rubies=engine.annotate(text);
        expect("寿司","すし",1,rubies,text);expect("食","た",4,rubies,text);
        text="君を思い出す";rubies=engine.annotate(text);
        expect("君","きみ",0,rubies,text);expect("思","おも",2,rubies,text);expect("出","だ",4,rubies,text);
        text="取り戻す";rubies=engine.annotate(text);
        expect("取","と",0,rubies,text);expect("戻","もど",2,rubies,text);
        text="今日も世界は美しい";rubies=engine.annotate(text);
        expect("今日","きょう",0,rubies,text);expect("世界","せかい",3,rubies,text);expect("美","うつく",6,rubies,text);
        text="😀世界と世界";rubies=engine.annotate(text);
        expect("世界","せかい",2,rubies,text);expect("世界","せかい",5,rubies,text);
        text="何もかもが違くみえたんだ。";rubies=engine.annotate(text);
        expect("違","ちが",5,rubies,text);
        text="違くない";rubies=engine.annotate(text);expect("違","ちが",0,rubies,text);
        text="違反";rubies=engine.annotate(text);expect("違反","いはん",0,rubies,text);
        text="間違い";rubies=engine.annotate(text);expect("間違","まちが",0,rubies,text);
        for (String sample:Arrays.asList("まだかなだけ","English lyrics","作詞：山田ゆうこ","𠮷野家で食べる","君を思い出す","😀君と世界、世界")) {
            for (ReadingEngine.Ruby ruby:engine.annotate(sample)) {
                require(ruby.start>=0 && ruby.end<=sample.length() && ruby.start<ruby.end,"UTF-16 bounds");
                require(sample.substring(ruby.start,ruby.end).codePoints().allMatch(ReadingEngine::han),"Kana must not receive duplicate readings");
                require(!ruby.reading.isEmpty() && !ruby.reading.contains("*"),"Missing readings must be skipped");
            }
        }
        require(engine.annotate("作詞：山田ゆうこ").isEmpty(),"Credits must not be annotated");
        String yrcText="目指したのは 蒼い 蒼い あの空";
        List<ReadingEngine.Ruby> yrcReadings=engine.annotate(yrcText);
        List<RubyLayout.Chunk> yrcChunks=RubyLayout.chunks(yrcText,Arrays.asList(Arrays.asList("目指したのは 蒼い"),Arrays.asList(" 蒼い あの空")),yrcReadings);
        require(yrcChunks.size()==2 && yrcChunks.stream().anyMatch(c->!c.rubies.isEmpty()),"Flattened YRC body must produce mapped readings");
        List<String> pending=new ArrayList<>(Arrays.asList("作曲：水野良樹"));
        LyricSnapshot loading=new LyricSnapshot(0,pending,0);
        pending.add(yrcText);
        LyricSnapshot complete=new LyricSnapshot(0,pending,1);
        require(!loading.japanese && complete.japanese && !loading.sameContent(complete),"Same cloud id must detect late Japanese lyric arrival");
        require(loading.texts.size()==1,"Snapshot must not alias mutable host lyric lists");
        require(complete.sameContent(new LyricSnapshot(0,pending,1)),"Repeated draw recovery must not restart identical analysis");
        require(!complete.sameContent(new LyricSnapshot(0,Arrays.asList("君を思い出す"),1)),"Same id with changed lyrics must invalidate");
        require(!complete.sameContent(new LyricSnapshot(0,pending,0)),"LRC to YRC metadata changes must invalidate");
        require(new LyricSnapshot(-123,Arrays.asList(yrcText),1).japanese,"Non-positive cloud/local ids must remain eligible");
        List<ReadingEngine.Ruby> synthetic=Arrays.asList(new ReadingEngine.Ruby(0,2,"せかい"),new ReadingEngine.Ruby(2,4,"せかい"));
        List<RubyLayout.Chunk> chunks=RubyLayout.chunks("世界世界",Arrays.asList(Arrays.asList("世界"),Arrays.asList("世界")),synthetic);
        require(chunks.size()==2 && chunks.get(0).offset==0 && chunks.get(1).offset==2,"Repeated words must keep distinct offsets");
        List<RubyLayout.Chunk> crossing=RubyLayout.chunks("世界",Arrays.asList(Arrays.asList("世"),Arrays.asList("界")),synthetic.subList(0,1));
        require(crossing.stream().allMatch(c->c.rubies.isEmpty()),"A reading must not be arbitrarily split at a wrap");
        require(RubyLayout.chunks("世界",Arrays.asList(Arrays.asList("missing")),synthetic).isEmpty(),"Unmatched text fails closed");
        List<Integer> heights=new ArrayList<>(Arrays.asList(40,42,24));
        RubyLayout.Reservation reservation=new RubyLayout.Reservation(heights,126,new boolean[]{true,true,false},30);
        for (int i=0;i<100;i++) reservation.apply(heights);
        require(heights.equals(Arrays.asList(70,72,24)),"Cached layout must not grow on each frame");
        require(reservation.appliedTotal==186 && reservation.matches(heights,186),"Total height and row heights must agree");
        reservation.restore(heights);
        require(heights.equals(Arrays.asList(40,42,24)),"Original layout must be recoverable");
        System.out.println("PASS: real IPADIC from APK, okurigana, compound readings, emoji offsets, repeated words, wrap boundary, idempotent height reservation");
        System.out.printf("Elapsed: %.2f s%n",(System.nanoTime()-before)/1e9);
    }
}
