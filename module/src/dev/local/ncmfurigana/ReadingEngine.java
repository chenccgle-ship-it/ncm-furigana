package dev.local.ncmfurigana;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Offline, UTF-16 offsets (the same indexing convention as Android Canvas). */
public final class ReadingEngine {
    public static final class Ruby {
        public final int start, end;
        public final String reading;
        Ruby(int start, int end, String reading) { this.start=start; this.end=end; this.reading=reading; }
    }
    private final Tokenizer tokenizer = new Tokenizer();

    public static boolean han(int cp) {
        return Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN || cp == 0x3005 || cp == 0x3007;
    }
    public static boolean hasHan(String text) { return text.codePoints().anyMatch(ReadingEngine::han); }
    public static boolean hasKana(String text) {
        return text.codePoints().anyMatch(cp -> (cp >= 0x3041 && cp <= 0x3096) || (cp >= 0x30A1 && cp <= 0x30FA));
    }
    public static boolean credit(String text) {
        return text.matches("(?i)^\\s*(作词|作曲|作詞|編曲|编曲|词|曲|歌|演唱|翻译|翻譯|制作|製作|lyrics|composer|vocal|arranger)\\s*[:：].*");
    }
    public static String hiragana(String input) {
        StringBuilder result=new StringBuilder();
        for (int i=0;i<input.length();i++) {
            char ch=input.charAt(i);
            result.append(ch >= '\u30a1' && ch <= '\u30f6' ? (char)(ch-0x60) : ch);
        }
        return result.toString();
    }
    public List<Ruby> annotate(String text) {
        if (text.length() > 2000 || !hasHan(text) || credit(text)) return Collections.emptyList();
        List<Ruby> result=new ArrayList<>();
        for (Token token:tokenizer.tokenize(text)) {
            String surface=token.getSurface(), reading=token.getReading();
            int start=token.getPosition();
            // IPADIC does not recognize the colloquial inflection 違く and splits its following kana.
            // Restrict the fallback to an unknown standalone 違 immediately followed by く.
            if ("*".equals(reading) && "違".equals(surface) && text.startsWith("違く",start)) reading="チガ";
            if (!hasHan(surface) || reading == null || reading.isEmpty() || reading.equals("*")) continue;
            if (start < 0 || start+surface.length() > text.length() || !text.regionMatches(start,surface,0,surface.length())) continue;
            result.addAll(align(surface, hiragana(reading), start));
        }
        return Collections.unmodifiableList(result);
    }

    /** Kana are literal anchors. Ambiguous splits are skipped rather than inventing readings. */
    static List<Ruby> align(String surface, String reading, int offset) {
        List<List<Ruby>> solutions=new ArrayList<>();
        if (surface.length() > 128 || reading.length() > 128) return Collections.emptyList();
        split(surface,reading,0,0,offset,new ArrayList<>(),solutions);
        return solutions.size()==1 ? solutions.get(0) : Collections.emptyList();
    }
    private static void split(String s,String r,int si,int ri,int offset,List<Ruby> path,List<List<Ruby>> solutions) {
        if (solutions.size()>1) return;
        if (si==s.length()) {
            if (ri==r.length()) solutions.add(new ArrayList<>(path));
            return;
        }
        boolean isHan=han(s.codePointAt(si));
        int end=si;
        while (end<s.length() && han(s.codePointAt(end))==isHan) end+=Character.charCount(s.codePointAt(end));
        if (!isHan) {
            String literal=hiragana(s.substring(si,end));
            if (r.startsWith(literal,ri)) split(s,r,end,ri+literal.length(),offset,path,solutions);
        } else if (end==s.length()) {
            if (ri<r.length()) {
                path.add(new Ruby(offset+si,offset+end,r.substring(ri)));
                split(s,r,end,r.length(),offset,path,solutions); path.remove(path.size()-1);
            }
        } else {
            int anchorEnd=end;
            while (anchorEnd<s.length() && !han(s.codePointAt(anchorEnd))) anchorEnd+=Character.charCount(s.codePointAt(anchorEnd));
            String anchor=hiragana(s.substring(end,anchorEnd));
            for (int cut=r.indexOf(anchor,ri+1);cut>=0;cut=r.indexOf(anchor,cut+1)) {
                path.add(new Ruby(offset+si,offset+end,r.substring(ri,cut)));
                split(s,r,end,cut,offset,path,solutions); path.remove(path.size()-1);
                if (solutions.size()>1) return;
            }
        }
    }
}
