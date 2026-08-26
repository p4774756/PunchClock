package com.example;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 每日一則英文諺語（依 Asia/Taipei 日期穩定挑選，與 ping-pong-server 對齊）。
 */
public final class DailyProverb {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Entry[] PROVERBS = {
            entry("The early bird catches the worm.", "早起的鳥兒有蟲吃。"),
            entry("Actions speak louder than words.", "行動勝於空談。"),
            entry("A journey of a thousand miles begins with a single step.", "千里之行，始於足下。"),
            entry("Practice makes perfect.", "熟能生巧。"),
            entry("Better late than never.", "遲到總比不到好。"),
            entry("Don't put all your eggs in one basket.", "不要把雞蛋放在同一個籃子裡。"),
            entry("Honesty is the best policy.", "誠實為上策。"),
            entry("Where there's a will, there's a way.", "有志者事竟成。"),
            entry("Rome wasn't built in a day.", "羅馬不是一天造成的。"),
            entry("Look before you leap.", "三思而後行。"),
            entry("Every cloud has a silver lining.", "烏雲背後總有陽光。"),
            entry("Time flies when you're having fun.", "歡樂時光總是過得特別快。"),
            entry("The pen is mightier than the sword.", "筆勝於劍。"),
            entry("Two heads are better than one.", "三個臭皮匠，勝過一個諸葛亮。"),
            entry("Slow and steady wins the race.", "穩紮穩打才能勝利。"),
            entry("Don't count your chickens before they hatch.", "別高興得太早。"),
            entry("A stitch in time saves nine.", "及時處理，事半功倍。"),
            entry("When in Rome, do as the Romans do.", "入鄉隨俗。"),
            entry("You can't have your cake and eat it too.", "魚與熊掌不可兼得。"),
            entry("Curiosity killed the cat.", "好奇心會惹禍上身。"),
            entry("All that glitters is not gold.", "閃閃發光的未必都是金子。"),
            entry("Birds of a feather flock together.", "物以類聚。"),
            entry("The grass is always greener on the other side.", "這山望著那山高。"),
            entry("Make hay while the sun shines.", "趁熱打鐵。"),
            entry("No pain, no gain.", "一分耕耘，一分收穫。"),
            entry("Strike while the iron is hot.", "打鐵要趁熱。"),
            entry("Fortune favors the brave.", "幸運眷顧勇者。"),
            entry("Knowledge is power.", "知識就是力量。"),
            entry("Out of sight, out of mind.", "眼不見為淨；離久情疏。"),
            entry("Prevention is better than cure.", "預防勝於治療。"),
            entry("The squeaky wheel gets the grease.", "會哭的孩子有糖吃。"),
            entry("There's no place like home.", "金窩銀窩，不如自己的狗窩。"),
            entry("Absence makes the heart grow fonder.", "小別勝新婚。"),
            entry("Beggars can't be choosers.", "饑不擇食。"),
            entry("Don't bite the hand that feeds you.", "不要恩將仇報。"),
            entry("Easy come, easy go.", "來得容易，去得也快。"),
            entry("Haste makes waste.", "欲速則不達。"),
            entry("It's never too late to learn.", "活到老，學到老。"),
            entry("Let sleeping dogs lie.", "別自找麻煩。"),
            entry("Many hands make light work.", "人多好辦事。")
    };

    private DailyProverb() {
    }

    public static Entry forToday() {
        return forDate(LocalDate.now(TAIPEI));
    }

    public static Entry forDate(LocalDate date) {
        String key = DAY_FMT.format(date);
        int index = (int) (hashDay(key) % PROVERBS.length);
        Entry base = PROVERBS[index];
        return new Entry(key, base.en, base.zh, index);
    }

    /** FNV-1a 32-bit，與伺服器 lib/dailyProverb.js 對齊 */
    static long hashDay(String key) {
        int h = 0x811c9dc5; // FNV offset basis = 2166136261
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            h ^= (b & 0xff);
            h *= 16777619;
        }
        return Integer.toUnsignedLong(h);
    }

    static int proverbCount() {
        return PROVERBS.length;
    }

    private static Entry entry(String en, String zh) {
        return new Entry(null, en, zh, -1);
    }

    public static final class Entry {
        public final String date;
        public final String en;
        public final String zh;
        public final int index;

        Entry(String date, String en, String zh, int index) {
            this.date = date;
            this.en = en;
            this.zh = zh;
            this.index = index;
        }
    }
}
