package com.example;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 每日一則六人行經典台詞（依 Asia/Taipei 日期穩定挑選，與 server 對齊）。
 */
public final class DailyProverb {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Entry[] PROVERBS = {
            entry("Joey: \"How you doin'?\"", "Joey：「你好嗎？」", "S1E24 · Joey 經典搭訕開場白"),
            entry("Ross: \"We were on a break!\"", "Ross：「我們只是在休息！」", "S3E15 · 和 Rachel 吵架，Ross 堅持「分手不算出軌」"),
            entry("Ross: \"Pivot! Pivot! Pivot!\"", "Ross：「轉！轉！轉！」", "S5E16 · 三人搬沙發卡樓梯，Ross 一直喊轉彎"),
            entry("Phoebe: \"Smelly cat, smelly cat, what are they feeding you?\"", "Phoebe：「臭貓，臭貓，他們都餵你什麼？」", "S2E14 · 在 Central Perk 唱〈Smelly Cat〉"),
            entry("Janice: \"Oh. My. God.\"", "Janice：「我的天啊。」", "S3E1 · Janice 突然現身，Chandler 又崩潰"),
            entry("Joey: \"Joey doesn't share food!\"", "Joey：「Joey 不分享食物！」", "S10E9 · 約會對象想分吃薯條，Joey 當場拒絕"),
            entry("Chandler: \"Could I BE any more...\"", "Chandler：「還能更……嗎？」", "S4E12 · 問答比賽前，Chandler 招牌反問句"),
            entry("Phoebe: \"He's her lobster.\"", "Phoebe：「他是她的龍蝦（命中注定的人）。」", "S2E14 · 用「龍蝦」比喻一輩子的伴侶"),
            entry("Monica: \"I know!\"", "Monica：「我知道！」", "S5E8 · 感恩節閃回，Monica 和 Chandler 秘密差點曝光"),
            entry("Ross: \"Unagi.\"", "Ross：「鰻魚（他自稱的完全警惕狀態）。」", "S6E17 · Ross 自創「Unagi」教 Rachel 防偷襲"),
            entry("Rachel: \"I got off the plane.\"", "Rachel：「我下飛機了。」", "S10E18 · 大結局 Rachel 下機回來找 Ross"),
            entry("Chandler: \"Can I interest you in a sarcastic comment?\"", "Chandler：「需要來點諷刺評論嗎？」", "S1E1 · 試播集，Chandler 一登場就毒舌"),
            entry("Joey: \"It's a moo point.\"", "Joey：「這是 moo point（像牛叫一樣，毫無意義）。」", "S7E8 · 把 moot point 聽成「牛在叫」，自創詞"),
            entry("Phoebe: \"They don't know that we know they know we know.\"", "Phoebe：「他們不知道我們知道他們知道我們知道。」", "S5E14 · 全員知道 Monica 和 Chandler 交往，互相試探"),
            entry("Ross: \"MY SANDWICH?!\"", "Ross：「我的三明治？！」", "S5E9 · 冰箱裡的「濕度適中」三明治被同事吃掉"),
            entry("Monica: \"Welcome to the real world. It sucks. You're gonna love it.\"", "Monica：「歡迎來到現實世界。很爛，但你會愛上的。」", "S1E1 · Rachel 逃婚來投靠，Monica 歡迎她進社會"),
            entry("Phoebe: \"That is brand new information!\"", "Phoebe：「這可是全新的消息！」", "S7E18 · 假裝驚訝，其實早就知道內情"),
            entry("Joey: \"London, baby!\"", "Joey：「倫敦，寶貝！」", "S4E23 · 飛去倫敦參加 Ross 婚禮，Joey 超興奮"),
            entry("Phoebe: \"Oh, I wish I could, but I don't want to.\"", "Phoebe：「哦，我很想，但我不想。」", "S7E13 · 不想幫忙又不好意思直說的經典藉口"),
            entry("Monica: \"Rules help control the fun!\"", "Monica：「規則能控制樂趣！」", "S6E12 · 遊戲夜 Monica 訂一堆規則控場"),
            entry("Chandler: \"I'm hopeless and awkward and desperate for love.\"", "Chandler：「我無望、尷尬，又渴望愛情。」", "S3E16 · 和 Janice 複合前，Chandler 深夜自白"),
            entry("Monica: \"Seven! Seven! Seven!\"", "Monica：「七！七！七！」", "S3E12 · 畫情慾地圖，7 是 Monica 的敏感帶"),
            entry("Chandler: \"It's not a purse! It's European!\"", "Chandler：「這不是手提包！這是歐洲款！」", "S5E13 · 幫 Joey 辯護他的「歐洲男用側包」"),
            entry("Joey: \"You are so far past the line, you can't even see the line.\"", "Joey：「你早就越線了，連線都看不見。」", "S10E11 · Joey 代班主持益智節目嗆參賽者"),
            entry("Rachel: \"It's like all my life everyone told me, 'You're a shoe!'\"", "Rachel：「好像這輩子大家都說：你是鞋子！」", "S3E2 · 趕婚禮時 Rachel 用「鞋子/帽子」比喻人生"),
            entry("Chandler: \"Sometimes I wish I was a lesbian... Did I say that out loud?\"", "Chandler：「有時我希望自己是女同志……我剛剛說出來了嗎？」", "S1E8 · 口說心聲，Chandler 經典社死"),
            entry("Ross: \"I'm fine. Totally fine.\"", "Ross：「我很好，完全沒事。」", "S2E11 · Rachel 和別人約會，Ross 嘴硬說沒事"),
            entry("Joey: \"How you doin'? ... Correct.\"", "Joey：「你好嗎？……正確。」", "S8E17 · Joey 教 Rachel 用搭訕句釣男人"),
            entry("Phoebe: \"This is brand new information!\"", "Phoebe：「這是全新的情報！」", "S7E18 · 再次假裝第一次聽說（Joey 提名艾美）"),
            entry("Chandler: \"I'm full, and yet I know if I stop eating this, I'll regret it.\"", "Chandler：「我飽了，但現在停我會後悔。」", "S3E25 · 海邊度假，Chandler 對甜點內心拉扯"),
            entry("Ross: \"Fine by me!\"", "Ross：「我沒意見！」", "S5E5 · 發現 Monica 和 Chandler 睡一起，假裝很 OK"),
            entry("Joey: \"You don't own a TV? What's all your furniture pointed at?\"", "Joey：「你沒電視？那家具都面向哪？」", "S1E4 · Joey 去 Ross 家，震驚沒有電視"),
            entry("Rachel: \"I'm gonna go get one of those job things.\"", "Rachel：「我要去找一份那種叫『工作』的東西。」", "S1E1 · 逃婚後 Rachel 決定開始自己賺錢"),
            entry("Monica: \"I KNOW!\"", "Monica：「我懂！」", "S5E8 · 聽到八卦時 Monica 激動附和（同集閃回）"),
            entry("Janice: \"Shut up! Shut up! Shut up!\"", "Janice：「閉嘴！閉嘴！閉嘴！」", "S4E15 · 和 Chandler 復合又吵架，Janice 連珠炮"),
            entry("Chandler: \"Could this BE any more awkward?\"", "Chandler：「還能更尷尬嗎？」", "S6E17 · Unagi 這集，Ross 讓場面超尷尬"),
            entry("Phoebe: \"L is for the way you look at me...\"", "Phoebe：「L 是你看我的方式……（唱情歌唱錯版）」", "S8E12 · 情人節唱〈Lollipop〉整段跑調"),
            entry("Ross: \"PIVOT!\"", "Ross：「轉——！」", "S5E16 · 搬沙發名場面單字版（同上集）"),
            entry("Joey: \"Paper! Snow! A ghost!\"", "Joey：「布！雪！鬼！」（經典 Fireball 遊戲）", "S3E9 · 感恩節玩美式足球，Joey 亂猜類別"),
            entry("Rachel: \"No uterus, no opinion.\"", "Rachel：「沒有子宮就別發表意見。」", "S8E12 · 懷孕話題，Rachel 嗆 Ross 別指手畫腳")
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
        return new Entry(key, base.en, base.zh, base.context, index);
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

    private static Entry entry(String en, String zh, String context) {
        return new Entry(null, en, zh, context, -1);
    }

    public static final class Entry {
        public final String date;
        public final String en;
        public final String zh;
        public final String context;
        public final int index;

        Entry(String date, String en, String zh, String context, int index) {
            this.date = date;
            this.en = en;
            this.zh = zh;
            this.context = context;
            this.index = index;
        }
    }
}
