package com.example;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 每日一則六人行經典台詞（依 Asia/Taipei 日期穩定挑選，與 ping-pong-server 對齊）。
 */
public final class DailyProverb {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Entry[] PROVERBS = {
            entry("Joey: \"How you doin'?\"", "Joey：「你好嗎？」"),
            entry("Ross: \"We were on a break!\"", "Ross：「我們只是在休息！」"),
            entry("Ross: \"Pivot! Pivot! Pivot!\"", "Ross：「轉！轉！轉！」"),
            entry("Phoebe: \"Smelly cat, smelly cat, what are they feeding you?\"", "Phoebe：「臭貓，臭貓，他們都餵你什麼？」"),
            entry("Janice: \"Oh. My. God.\"", "Janice：「我的天啊。」"),
            entry("Joey: \"Joey doesn't share food!\"", "Joey：「Joey 不分享食物！」"),
            entry("Chandler: \"Could I BE any more...\"", "Chandler：「還能更……嗎？」"),
            entry("Phoebe: \"He's her lobster.\"", "Phoebe：「他是她的龍蝦（命中注定的人）。」"),
            entry("Monica: \"I know!\"", "Monica：「我知道！」"),
            entry("Ross: \"Unagi.\"", "Ross：「鰻魚（他自稱的完全警惕狀態）。」"),
            entry("Rachel: \"I got off the plane.\"", "Rachel：「我下飛機了。」"),
            entry("Chandler: \"Can I interest you in a sarcastic comment?\"", "Chandler：「需要來點諷刺評論嗎？」"),
            entry("Joey: \"It's a moo point.\"", "Joey：「這是 moo point（像牛叫一樣，毫無意義）。」"),
            entry("Phoebe: \"They don't know that we know they know we know.\"", "Phoebe：「他們不知道我們知道他們知道我們知道。」"),
            entry("Ross: \"MY SANDWICH?!\"", "Ross：「我的三明治？！」"),
            entry("Monica: \"Welcome to the real world. It sucks. You're gonna love it.\"", "Monica：「歡迎來到現實世界。很爛，但你會愛上的。」"),
            entry("Phoebe: \"That is brand new information!\"", "Phoebe：「這可是全新的消息！」"),
            entry("Joey: \"London, baby!\"", "Joey：「倫敦，寶貝！」"),
            entry("Phoebe: \"Oh, I wish I could, but I don't want to.\"", "Phoebe：「哦，我很想，但我不想。」"),
            entry("Monica: \"Rules help control the fun!\"", "Monica：「規則能控制樂趣！」"),
            entry("Chandler: \"I'm hopeless and awkward and desperate for love.\"", "Chandler：「我無望、尷尬，又渴望愛情。」"),
            entry("Monica: \"Seven! Seven! Seven!\"", "Monica：「七！七！七！」"),
            entry("Chandler: \"It's not a purse! It's European!\"", "Chandler：「這不是手提包！這是歐洲款！」"),
            entry("Joey: \"You are so far past the line, you can't even see the line.\"", "Joey：「你早就越線了，連線都看不見。」"),
            entry("Rachel: \"It's like all my life everyone told me, 'You're a shoe!'\"", "Rachel：「好像這輩子大家都說：你是鞋子！」"),
            entry("Chandler: \"Sometimes I wish I was a lesbian... Did I say that out loud?\"", "Chandler：「有時我希望自己是女同志……我剛剛說出來了嗎？」"),
            entry("Ross: \"I'm fine. Totally fine.\"", "Ross：「我很好，完全沒事。」"),
            entry("Joey: \"How you doin'? ... Correct.\"", "Joey：「你好嗎？……正確。」"),
            entry("Phoebe: \"This is brand new information!\"", "Phoebe：「這是全新的情報！」"),
            entry("Chandler: \"I'm full, and yet I know if I stop eating this, I'll regret it.\"", "Chandler：「我飽了，但現在停我會後悔。」"),
            entry("Ross: \"Fine by me!\"", "Ross：「我沒意見！」"),
            entry("Joey: \"You don't own a TV? What's all your furniture pointed at?\"", "Joey：「你沒電視？那家具都面向哪？」"),
            entry("Rachel: \"I'm gonna go get one of those job things.\"", "Rachel：「我要去找一份那種叫『工作』的東西。」"),
            entry("Monica: \"I KNOW!\"", "Monica：「我懂！」"),
            entry("Janice: \"Shut up! Shut up! Shut up!\"", "Janice：「閉嘴！閉嘴！閉嘴！」"),
            entry("Chandler: \"Could this BE any more awkward?\"", "Chandler：「還能更尷尬嗎？」"),
            entry("Phoebe: \"L is for the way you look at me...\"", "Phoebe：「L 是你看我的方式……（唱情歌唱錯版）」"),
            entry("Ross: \"PIVOT!\"", "Ross：「轉——！」"),
            entry("Joey: \"Paper! Snow! A ghost!\"", "Joey：「布！雪！鬼！」（經典 Fireball 遊戲）"),
            entry("Rachel: \"No uterus, no opinion.\"", "Rachel：「沒有子宮就別發表意見。」")
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
