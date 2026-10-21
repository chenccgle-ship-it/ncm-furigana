package dev.local.ncmfurigana;

public final class PlaybackPolicyTest {
    public static void main(String[] args) {
        check(!PlaybackPolicy.needsFallback("https://music.example/full.mp3",false,200),"Keep normal full URL");
        check(PlaybackPolicy.needsFallback("https://music.example/trial.mp3",true,200),"Trial URL still needs matching");
        check(PlaybackPolicy.needsFallback(null,false,404),"Unavailable track needs matching");
        check(PlaybackPolicy.needsFallback("",false,200),"Empty success payload needs matching");
        System.out.println("PASS: full original preserved; trial and unavailable tracks select fallback");
    }
    private static void check(boolean value,String name) {if(!value) throw new AssertionError(name);}
}
