package dev.local.ncmfurigana;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Identity is the lyric content, not the official/cloud song id alone. */
public final class LyricSnapshot {
    public final long id;
    public final List<String> texts;
    public final int yrcLines;
    public final boolean japanese;
    public LyricSnapshot(long id,List<String> texts,int yrcLines) {
        this.id=id;
        this.texts=Collections.unmodifiableList(new ArrayList<>(texts));
        this.yrcLines=yrcLines;
        boolean kana=false;
        for (String text:texts) if (!ReadingEngine.credit(text) && ReadingEngine.hasKana(text)) { kana=true;break; }
        japanese=kana;
    }
    public boolean sameContent(LyricSnapshot other) {
        return other!=null && id==other.id && yrcLines==other.yrcLines && texts.equals(other.texts);
    }
}
