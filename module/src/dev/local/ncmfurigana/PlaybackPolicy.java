package dev.local.ncmfurigana;

/** Kept independent of Android so full URLs, trials and misses can be regression-tested. */
final class PlaybackPolicy {
    static boolean needsFallback(String url,boolean trial,int code) {
        return url==null||url.trim().isEmpty()||trial||code!=200;
    }
}
