package dev.local.ncmfurigana;

import java.util.ArrayList;
import java.util.List;

/** Pure layout bookkeeping, separately testable without Android. */
public final class RubyLayout {
    public static final class Chunk {
        public final String text;
        public final int offset, row;
        public final List<ReadingEngine.Ruby> rubies;
        Chunk(String text,int offset,int row,List<ReadingEngine.Ruby> rubies) {
            this.text=text;this.offset=offset;this.row=row;this.rubies=rubies;
        }
    }
    public static List<Chunk> chunks(String original,List<List<String>> rows,List<ReadingEngine.Ruby> rubies) {
        List<Chunk> result=new ArrayList<>();
        int cursor=0;
        for (int row=0;row<rows.size();row++) {
            for (String text:rows.get(row)) {
                if (text == null || text.isEmpty()) continue;
                int offset=original.indexOf(text,cursor);
                // Layout must preserve original order. Never search backward into another occurrence.
                if (offset<0) return new ArrayList<>();
                List<ReadingEngine.Ruby> selected=new ArrayList<>();
                for (ReadingEngine.Ruby ruby:rubies) {
                    if (ruby.start>=offset && ruby.end<=offset+text.length()) selected.add(ruby);
                }
                result.add(new Chunk(text,offset,row,selected));
                cursor=offset+text.length();
            }
        }
        return result;
    }

    public static final class Reservation {
        public final int[] base, applied;
        public final int baseTotal, appliedTotal;
        public Reservation(List<Integer> heights,int total,boolean[] rubyRows,int space) {
            base=new int[heights.size()]; applied=new int[heights.size()];baseTotal=total;
            int delta=0;
            for (int i=0;i<heights.size();i++) {
                base[i]=heights.get(i);
                int extra=i<rubyRows.length && rubyRows[i] ? space : 0;
                applied[i]=base[i]+extra;delta+=extra;
            }
            appliedTotal=total+delta;
        }
        public boolean matches(List<Integer> heights,int total) {
            if (total!=appliedTotal || heights.size()!=applied.length) return false;
            for (int i=0;i<applied.length;i++) if (heights.get(i)!=applied[i]) return false;
            return true;
        }
        public void apply(List<Integer> heights) {
            for (int i=0;i<applied.length;i++) heights.set(i,applied[i]);
        }
        public void restore(List<Integer> heights) {
            for (int i=0;i<base.length;i++) heights.set(i,base[i]);
        }
    }
}
