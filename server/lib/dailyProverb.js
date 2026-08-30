/**
 * 每日一則六人行經典台詞（依 Asia/Taipei 日期穩定挑選，同日不變）。
 * context：第幾季第幾集 + 簡短情境（台灣慣用 S#E# 標法）。
 */

const PROVERBS = [
  { en: 'Joey: "How you doin\'?"', zh: 'Joey：「你好嗎？」', context: 'S1E24 · Joey 經典搭訕開場白' },
  { en: 'Ross: "We were on a break!"', zh: 'Ross：「我們只是在休息！」', context: 'S3E15 · 和 Rachel 吵架，Ross 堅持「分手不算出軌」' },
  { en: 'Ross: "Pivot! Pivot! Pivot!"', zh: 'Ross：「轉！轉！轉！」', context: 'S5E16 · 三人搬沙發卡樓梯，Ross 一直喊轉彎' },
  { en: 'Phoebe: "Smelly cat, smelly cat, what are they feeding you?"', zh: 'Phoebe：「臭貓，臭貓，他們都餵你什麼？」', context: 'S2E14 · 在 Central Perk 唱〈Smelly Cat〉' },
  { en: 'Janice: "Oh. My. God."', zh: 'Janice：「我的天啊。」', context: 'S3E1 · Janice 突然現身，Chandler 又崩潰' },
  { en: 'Joey: "Joey doesn\'t share food!"', zh: 'Joey：「Joey 不分享食物！」', context: 'S10E9 · 約會對象想分吃薯條，Joey 當場拒絕' },
  { en: 'Chandler: "Could I BE any more..."', zh: 'Chandler：「還能更……嗎？」', context: 'S4E12 · 問答比賽前，Chandler 招牌反問句' },
  { en: 'Phoebe: "He\'s her lobster."', zh: 'Phoebe：「他是她的龍蝦（命中注定的人）。」', context: 'S2E14 · 用「龍蝦」比喻一輩子的伴侶' },
  { en: 'Monica: "I know!"', zh: 'Monica：「我知道！」', context: 'S5E8 · 感恩節閃回，Monica 和 Chandler 秘密差點曝光' },
  { en: 'Ross: "Unagi."', zh: 'Ross：「鰻魚（他自稱的完全警惕狀態）。」', context: 'S6E17 · Ross 自創「Unagi」教 Rachel 防偷襲' },
  { en: 'Rachel: "I got off the plane."', zh: 'Rachel：「我下飛機了。」', context: 'S10E18 · 大結局 Rachel 下機回來找 Ross' },
  { en: 'Chandler: "Can I interest you in a sarcastic comment?"', zh: 'Chandler：「需要來點諷刺評論嗎？」', context: 'S1E1 · 試播集，Chandler 一登場就毒舌' },
  { en: 'Joey: "It\'s a moo point."', zh: 'Joey：「這是 moo point（像牛叫一樣，毫無意義）。」', context: 'S7E8 · 把 moot point 聽成「牛在叫」，自創詞' },
  { en: 'Phoebe: "They don\'t know that we know they know we know."', zh: 'Phoebe：「他們不知道我們知道他們知道我們知道。」', context: 'S5E14 · 全員知道 Monica 和 Chandler 交往，互相試探' },
  { en: 'Ross: "MY SANDWICH?!"', zh: 'Ross：「我的三明治？！」', context: 'S5E9 · 冰箱裡的「濕度適中」三明治被同事吃掉' },
  { en: 'Monica: "Welcome to the real world. It sucks. You\'re gonna love it."', zh: 'Monica：「歡迎來到現實世界。很爛，但你會愛上的。」', context: 'S1E1 · Rachel 逃婚來投靠，Monica 歡迎她進社會' },
  { en: 'Phoebe: "That is brand new information!"', zh: 'Phoebe：「這可是全新的消息！」', context: 'S7E18 · 假裝驚訝，其實早就知道內情' },
  { en: 'Joey: "London, baby!"', zh: 'Joey：「倫敦，寶貝！」', context: 'S4E23 · 飛去倫敦參加 Ross 婚禮，Joey 超興奮' },
  { en: 'Phoebe: "Oh, I wish I could, but I don\'t want to."', zh: 'Phoebe：「哦，我很想，但我不想。」', context: 'S7E13 · 不想幫忙又不好意思直說的經典藉口' },
  { en: 'Monica: "Rules help control the fun!"', zh: 'Monica：「規則能控制樂趣！」', context: 'S6E12 · 遊戲夜 Monica 訂一堆規則控場' },
  { en: 'Chandler: "I\'m hopeless and awkward and desperate for love."', zh: 'Chandler：「我無望、尷尬，又渴望愛情。」', context: 'S3E16 · 和 Janice 複合前，Chandler 深夜自白' },
  { en: 'Monica: "Seven! Seven! Seven!"', zh: 'Monica：「七！七！七！」', context: 'S3E12 · 畫情慾地圖，7 是 Monica 的敏感帶' },
  { en: 'Chandler: "It\'s not a purse! It\'s European!"', zh: 'Chandler：「這不是手提包！這是歐洲款！」', context: 'S5E13 · 幫 Joey 辯護他的「歐洲男用側包」' },
  { en: 'Joey: "You are so far past the line, you can\'t even see the line."', zh: 'Joey：「你早就越線了，連線都看不見。」', context: 'S10E11 · Joey 代班主持益智節目嗆參賽者' },
  { en: 'Rachel: "It\'s like all my life everyone told me, \'You\'re a shoe!\'"', zh: 'Rachel：「好像這輩子大家都說：你是鞋子！」', context: 'S3E2 · 趕婚禮時 Rachel 用「鞋子/帽子」比喻人生' },
  { en: 'Chandler: "Sometimes I wish I was a lesbian... Did I say that out loud?"', zh: 'Chandler：「有時我希望自己是女同志……我剛剛說出來了嗎？」', context: 'S1E8 · 口說心聲，Chandler 經典社死' },
  { en: 'Ross: "I\'m fine. Totally fine."', zh: 'Ross：「我很好，完全沒事。」', context: 'S2E11 · Rachel 和別人約會，Ross 嘴硬說沒事' },
  { en: 'Joey: "How you doin\'? ... Correct."', zh: 'Joey：「你好嗎？……正確。」', context: 'S8E17 · Joey 教 Rachel 用搭訕句釣男人' },
  { en: 'Phoebe: "This is brand new information!"', zh: 'Phoebe：「這是全新的情報！」', context: 'S7E18 · 再次假裝第一次聽說（Joey 提名艾美）' },
  { en: 'Chandler: "I\'m full, and yet I know if I stop eating this, I\'ll regret it."', zh: 'Chandler：「我飽了，但現在停我會後悔。」', context: 'S3E25 · 海邊度假，Chandler 對甜點內心拉扯' },
  { en: 'Ross: "Fine by me!"', zh: 'Ross：「我沒意見！」', context: 'S5E5 · 發現 Monica 和 Chandler 睡一起，假裝很 OK' },
  { en: 'Joey: "You don\'t own a TV? What\'s all your furniture pointed at?"', zh: 'Joey：「你沒電視？那家具都面向哪？」', context: 'S1E4 · Joey 去 Ross 家，震驚沒有電視' },
  { en: 'Rachel: "I\'m gonna go get one of those job things."', zh: 'Rachel：「我要去找一份那種叫『工作』的東西。」', context: 'S1E1 · 逃婚後 Rachel 決定開始自己賺錢' },
  { en: 'Monica: "I KNOW!"', zh: 'Monica：「我懂！」', context: 'S5E8 · 聽到八卦時 Monica 激動附和（同集閃回）' },
  { en: 'Janice: "Shut up! Shut up! Shut up!"', zh: 'Janice：「閉嘴！閉嘴！閉嘴！」', context: 'S4E15 · 和 Chandler 復合又吵架，Janice 連珠炮' },
  { en: 'Chandler: "Could this BE any more awkward?"', zh: 'Chandler：「還能更尷尬嗎？」', context: 'S6E17 · Unagi 這集，Ross 讓場面超尷尬' },
  { en: 'Phoebe: "L is for the way you look at me..."', zh: 'Phoebe：「L 是你看我的方式……（唱情歌唱錯版）」', context: 'S8E12 · 情人節唱〈Lollipop〉整段跑調' },
  { en: 'Ross: "PIVOT!"', zh: 'Ross：「轉——！」', context: 'S5E16 · 搬沙發名場面單字版（同上集）' },
  { en: 'Joey: "Paper! Snow! A ghost!"', zh: 'Joey：「布！雪！鬼！」（經典 Fireball 遊戲）', context: 'S3E9 · 感恩節玩美式足球，Joey 亂猜類別' },
  { en: 'Rachel: "No uterus, no opinion."', zh: 'Rachel：「沒有子宮就別發表意見。」', context: 'S8E12 · 懷孕話題，Rachel 嗆 Ross 別指手畫腳' }
];

function dateKeyInTaipei(now = new Date()) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Taipei',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(now);
}

/** FNV-1a 32-bit，與桌面端演算法對齊 */
function hashDay(key) {
  let h = 2166136261;
  for (let i = 0; i < key.length; i++) {
    h ^= key.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function getDailyProverb(now = new Date()) {
  const date = dateKeyInTaipei(now);
  const index = hashDay(date) % PROVERBS.length;
  const item = PROVERBS[index];
  return {
    date,
    en: item.en,
    zh: item.zh,
    context: item.context,
    index
  };
}

module.exports = {
  PROVERBS,
  getDailyProverb,
  dateKeyInTaipei,
  hashDay
};
