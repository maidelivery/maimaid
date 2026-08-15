import Foundation

nonisolated enum SearchTextNormalizer {
    private static let traditionalToSimplified = StringTransform("Traditional-Simplified")
    // Sources: github.com/BYVoid/OpenCC JPShinjitai/ST/TS and
    // unicode.org/Public/UCD/latest/ucd/Unihan.zip (single-character aliases).
    // Ambiguous semantic entries (弁 and 沪) are intentionally omitted.
    private static let variantPairs = "㦳㘽䀋盐䎛㖈䱎䱍両两並并乘乗亀龟亂乱亙亘亜亚亞亚仏佛伝传併并來来価价俠侠倂并倶俱倹俭值値假仮偽伪傳传僞伪價价儉俭兎兔児儿兒儿內内兩两具俱円圆冰氷凜凛処处剎刹剣剑剤剂剩剰劍剑劑剂労劳勞劳勧劝勲勋勳勋勵励勸劝勻匀區区単单卻却卽即厠厕厳严參参吳吴呉吴咒呪唖哑啞哑單单営营噓嘘噛啮嚙啮嚴严囑嘱団团囲围図图圈圏國国圍围圓圆圖图團团圧压堯尧塁垒塩盐填塡增増墮堕壊坏壓压壘垒壞坏壤壌壯壮売卖壹壱壽寿壿墫変变奧奥奨奖奬奖妝妆姊姉姬姫娛娱娯娱媯妫嬀妫嬢娘孃娘學学実实寛宽寢寝實实寫写寬宽寶宝対对専专將将專专對对尭尧屆届屬属峯峰峽峡嶽岳巌岩巖岩巢巣巻卷帯带帰归帲帡帶带庁厅広广廁厕廃废廢废廣广廳厅弐贰弾弹彈弹彌弥彎弯彥彦徑径従从從从徴征徵征德徳応应恆恒悅悦悩恼悪恶惠恵惡恶惱恼愼慎慘惨應应懐怀懷怀戀恋戦战戯戏戰战戲戏戶户戸户戾戻択择拂払拔抜拜拝拠据拡扩挙举挩捝挾挟捜搜插挿揭掲揷挿揺摇搉㩁搔掻搖摇摂摄摑掴撃击撹搅擇择擊击擔担據据擧举擴扩攝摄攪搅收収效効敍叙敓敚敕勅敘叙數数斉齐斎斋斷断晄晃晉晋晚晩晝昼暁晓暦历暨曁曆历曉晓曶㫚曾曽會会枡桝查査栄荣桜樱桟栈條条梲棁棧栈検检楽乐榆楡榮荣槇槙様样槩㮣槪概樂乐樓楼樞枢樣样樧榝権权橫横檜桧檢检櫻樱權权歐欧歓欢歡欢步歩歯齿歲岁歳岁歴历歷历歸归殘残殻壳殼壳毆殴每毎気气氣气污汚沒没沢泽浄净浜滨涉渉涗涚涙泪淚泪淨净淺浅渇渴済济渋涩渓溪満满溈沩溌泼溫温溼湿滝泷滯滞滿满潑泼潙沩潛潜澀涩澁涩澤泽濕湿濟济濤涛濱滨瀧泷瀨濑瀬濑灣湾為为焔焰焼烧煙烟燈灯燒烧營营爐炉爭争爲为牀床犠牺犧牺狀状狹狭猟猎獎奖獣兽獨独獵猎獸兽獻献甁瓶產产産产畫画畳叠畵画當当疊叠痹痺瘦痩癡痴発发發发皋皐盜盗盡尽県县眞真硏研硷碱碎砕礪砺祕秘祿禄禦御禪禅禮礼禰祢禱祷稅税稜棱稱称稻稲穂穗穎颖穏稳穩稳穰穣竃灶竈灶竊窃竜龙竝并粛肃粧妆粹粋糉粽糸丝経经絕绝絚絙絲丝絵绘絶绝經经継继続续綠绿総总緑绿緒绪緖绪緣缘縁缘縄绳縣县縦纵縱纵總总繊纤繋系繍绣繡绣繩绳繪绘繫系繼继續续纔才纖纤缺欠罐缶羣群聡聪聯联聰聪聲声聴听聽听肅肃脣唇脫脱脳脑腁胼腦脑腳脚膽胆臓脏臟脏臺台與与舉举舊旧舎舍舖铺舗铺艶艳艷艳芸艺茉苿荔茘荘庄莊庄莖茎菸烟萊莱萠萌萬万蔣蒋蔥葱蔿蒍薫熏薬药薰熏藏蔵藝艺藥药蘆芦虁蘷處处虛虚號号蛍萤蛻蜕蝋蜡螢萤蟬蝉蟲虫蠟蜡蠶蚕蠻蛮衛卫衞卫裝装襃褒覚觉覧览観观覺觉覽览觀观觸触訳译証证詽訮說说説说読读謠谣謡谣證证譯译譲让譽誉讀读變变讓让豊丰豐丰豣豜豫予貓猫貳贰賛赞賣卖賴赖贊赞贋赝贗赝跺跥踐践躛躗転转軽轻輌辆輕轻輛辆輧軿轉转辭辞辺边连联逓递連联遅迟遙遥遞递遲迟邊边郞郎郷乡鄉乡鄕乡鄰邻酔醉醗酦醤酱醫医醬酱醱酦醸酿釀酿釈释釋释鉄铁銭钱銳锐鋪铺鋭锐鋳铸錄录錢钱錬炼録录鍊炼鎭镇鎮镇鐵铁鑄铸鑛鉱链炼関关閱阅閲阅闘斗關关陷陥険险隠隐隣邻隨随險险隱隐隷隶隸隶雑杂雙双雜杂雞鸡霊灵霸覇靈灵靜静頴颖頼赖顏颜顔颜顕显顯显颕颖飜翻飮饮飲饮餅饼餘余餠饼馀余駅驿駆驱駈驱騒骚験验騷骚驅驱驗验驛驿髓髄體体髪发髮发鬥斗鬪斗鬭斗鱉鳖鴎鸥鶏鸡鷄鸡鷗鸥鹸碱鹼碱鹽盐麥麦麪面麴曲麵面麹曲麺面黃黄黒黑默黙點点黨党鼈鳖齊齐齋斋齒齿齡龄齢龄龍龙龜龟"
    private static let compatibilityPairs = "髙高﨑崎邉边邊边邨村神神塚塚晴晴羽羽㐂喜福福靖靖都都侮侭審审"
    private static let variantMap: [Character: Character] = {
        let characters = Array(variantPairs + compatibilityPairs)
        var map = [Character: Character](minimumCapacity: characters.count / 2)
        for index in stride(from: 0, to: characters.count - 1, by: 2) {
            map[characters[index]] = characters[index + 1]
        }
        return map
    }()

    static func normalized(_ value: String) -> String {
        let transformed = value.applyingTransform(traditionalToSimplified, reverse: false) ?? value
        let canonical = String(transformed.map { variantMap[$0] ?? $0 })
        return canonical
            .folding(options: [.caseInsensitive, .diacriticInsensitive], locale: .current)
            .lowercased()
    }

    static func compact(_ value: String) -> String {
        normalized(value).filter { !$0.isWhitespace }
    }

    static func matches(
        _ value: String,
        normalizedQuery: String,
        compactQuery: String,
    ) -> Bool {
        let normalizedValue = normalized(value)
        return normalizedValue.localizedStandardContains(normalizedQuery) ||
            normalizedValue.filter { !$0.isWhitespace }.localizedStandardContains(compactQuery)
    }
}
