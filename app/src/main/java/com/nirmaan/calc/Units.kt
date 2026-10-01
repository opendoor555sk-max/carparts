package com.nirmaan.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/*
 * "Sab Unit": every kind of unit of the world in one converter + calculator.
 * Pure Kotlin (no Android) so it can be unit tested.
 * Each unit:  value_in_base = value × k + off      (inv units: value_in_base = k / value)
 */

class UCat(val id: String, val icon: String, val en: String, val hi: String, val gu: String, val color: Long) {
    fun name(lang: Int) = when (lang) { 1 -> hi; 2 -> gu; else -> en }
}

class UDef(
    val id: String, val cat: String, val k: Double,
    val en: String, val hi: String, val gu: String,
    val sym: String, val cc: String, val aka: List<String>, val note: String,
    val off: Double, val inv: Boolean, val pop: Int
) {
    fun name(lang: Int) = when (lang) { 1 -> hi.ifEmpty { en }; 2 -> gu.ifEmpty { en }; else -> en }
    fun toBase(v: Double) = if (inv) k / v else v * k + off
    fun fromBase(b: Double) = if (inv) k / b else (b - off) / k
    /** flag of the country the unit belongs to; 🌐 = used everywhere */
    val flag get() = Units.flag(cc)
}

/** a thing everybody knows, to feel how big a value is */
class URef(val cat: String, val v: Double, val en: String, val hi: String, val gu: String)

object Units {
    // ---------------- categories ----------------
    val CATS = listOf(
        UCat("len", "📏", "Length", "लंबाई", "લંબાઈ", 0xFF1565C0),
        UCat("area", "🌾", "Area / Land", "ज़मीन / क्षेत्रफल", "જમીન / ક્ષેત્રફળ", 0xFF2E7D32),
        UCat("mass", "⚖️", "Weight", "वज़न", "વજન", 0xFF6A1B9A),
        UCat("vol", "🧊", "Volume", "आयतन", "ઘનફળ", 0xFF00838F),
        UCat("temp", "🌡️", "Temperature", "तापमान", "તાપમાન", 0xFFC62828),
        UCat("time", "⏱️", "Time", "समय", "સમય", 0xFF455A64),
        UCat("speed", "🚗", "Speed", "रफ़्तार", "ઝડપ", 0xFFEF6C00),
        UCat("press", "💨", "Pressure", "दबाव", "દબાણ", 0xFF5C6BC0),
        UCat("energy", "🔥", "Energy", "ऊर्जा", "ઊર્જા", 0xFFD84315),
        UCat("power", "⚡", "Power", "पावर / शक्ति", "પાવર", 0xFFF9A825),
        UCat("count", "🔢", "Numbers", "गिनती", "ગણતરી", 0xFF8D6E63),
        UCat("angle", "📐", "Angle", "कोण", "ખૂણો", 0xFF00695C),
        UCat("data", "💾", "Data", "डेटा", "ડેટા", 0xFF37474F),
        UCat("force", "💪", "Force", "बल", "બળ", 0xFF795548),
        UCat("torque", "🔧", "Torque", "टॉर्क", "ટોર્ક", 0xFF6D4C41),
        UCat("flow", "🌊", "Flow", "बहाव", "વહેણ", 0xFF0277BD),
        UCat("dens", "🧱", "Density", "घनत्व", "ઘનતા", 0xFF8D6E63),
        UCat("fuel", "⛽", "Mileage", "माइलेज", "માઇલેજ", 0xFF558B2F),
        UCat("acc", "🚀", "Acceleration", "त्वरण", "પ્રવેગ", 0xFF4527A0),
        UCat("freq", "📻", "Frequency", "आवृत्ति", "આવૃત્તિ", 0xFF00897B),
        UCat("volt", "🔌", "Voltage", "वोल्टेज", "વોલ્ટેજ", 0xFFFFA000),
        UCat("curr", "〰️", "Current", "करंट", "કરંટ", 0xFFFF8F00),
        UCat("res", "Ω", "Resistance", "प्रतिरोध", "અવરોધ", 0xFF6D4C41),
        UCat("charge", "🔋", "Charge / Battery", "चार्ज / बैटरी", "ચાર્જ / બેટરી", 0xFF2E7D32),
        UCat("cap", "⚙️", "Capacitance", "कैपेसिटेंस", "કેપેસિટન્સ", 0xFF546E7A),
        UCat("ind", "🌀", "Inductance", "इंडक्टेंस", "ઇન્ડક્ટન્સ", 0xFF546E7A),
        UCat("light", "💡", "Light (lux)", "रोशनी", "પ્રકાશ", 0xFFFBC02D),
        UCat("rad", "☢️", "Radiation", "रेडिएशन", "રેડિયેશન", 0xFF9E9D24),
        UCat("dose", "🩻", "Radiation dose", "रेडिएशन डोज़", "રેડિયેશન ડોઝ", 0xFF827717),
        UCat("mag", "🧲", "Magnetic field", "चुंबकीय क्षेत्र", "ચુંબકીય ક્ષેત્ર", 0xFFAD1457),
        UCat("conc", "🧪", "Concentration", "मात्रा (ppm, %)", "પ્રમાણ (ppm, %)", 0xFF00838F),
        UCat("visc", "🛢️", "Viscosity", "गाढ़ापन", "ઘટ્ટતા", 0xFF4E342E),
        UCat("amount", "⚗️", "Amount (mole)", "मोल", "મોલ", 0xFF5D4037)
    )

    val UNITS = ArrayList<UDef>()
    val REFS = ArrayList<URef>()

    private fun u(
        id: String, cat: String, k: Double, en: String, hi: String = "", gu: String = "", sym: String = "",
        cc: String = "", aka: String = "", note: String = "", off: Double = 0.0, inv: Boolean = false, pop: Int = 0
    ) { UNITS.add(UDef(id, cat, k, en, hi, gu, sym, cc, aka.split(',').map { it.trim() }.filter { it.isNotEmpty() }, note, off, inv, pop)) }

    private fun ref(cat: String, v: Double, en: String, hi: String, gu: String) { REFS.add(URef(cat, v, en, hi, gu)) }

    init {
        // ======================= LENGTH (base: metre) =======================
        val L = "len"
        u("planck", L, 1.616255e-35, "Planck length", "प्लैंक लंबाई", "પ્લાન્ક લંબાઈ", "ℓP", note = "sabse chhoti lambai")
        u("fm", L, 1e-15, "femtometre (fermi)", "फेम्टोमीटर", "ફેમ્ટોમીટર", "fm", aka = "fermi")
        u("pm", L, 1e-12, "picometre", "पिकोमीटर", "પિકોમીટર", "pm")
        u("ang", L, 1e-10, "ångström", "एंगस्ट्रॉम", "એંગસ્ટ્રોમ", "Å", aka = "angstrom")
        u("nm", L, 1e-9, "nanometre", "नैनोमीटर", "નેનોમીટર", "nm", aka = "nanometer")
        u("um", L, 1e-6, "micron (µm)", "माइक्रोन", "માઇક્રોન", "µm", aka = "micron,micrometer,micrometre,um")
        u("thou", L, 2.54e-5, "thou / mil", "थाउ", "થાઉ", "mil", aka = "thou")
        u("mm", L, 1e-3, "millimetre", "मिलीमीटर", "મિલીમીટર", "mm", aka = "millimeter,mili", pop = 3)
        u("cm", L, 0.01, "centimetre", "सेंटीमीटर", "સેન્ટીમીટર", "cm", aka = "centimeter,senti", pop = 4)
        u("in", L, 0.0254, "inch", "इंच", "ઇંચ", "in", aka = "inches,\",inch", pop = 5)
        u("dm", L, 0.1, "decimetre", "डेसीमीटर", "ડેસીમીટર", "dm")
        u("hand", L, 0.1016, "hand", "हैंड", "હેન્ડ", "hh")
        u("ft", L, 0.3048, "foot", "फुट", "ફૂટ", "ft", aka = "feet,foot,fut,',futt", pop = 6)
        u("yd", L, 0.9144, "yard", "गज़", "ગજ", "yd", aka = "yard,yards,gaj,gaz", pop = 3)
        u("m", L, 1.0, "metre", "मीटर", "મીટર", "m", aka = "meter,meters,metres,mtr", pop = 6)
        u("fathom", L, 1.8288, "fathom", "फैदम", "ફેધમ", "ftm")
        u("rod", L, 5.0292, "rod / pole / perch", "रॉड", "રોડ", "rd", "GB", "pole,perch")
        u("chain", L, 20.1168, "chain", "चेन (ज़रीब)", "ચેન", "ch", "GB", "jarib,zarib")
        u("furlong", L, 201.168, "furlong", "फर्लांग", "ફર્લાંગ", "fur", "GB")
        u("cable", L, 185.2, "cable", "केबल", "કેબલ", "", "", "cable length")
        u("km", L, 1000.0, "kilometre", "किलोमीटर", "કિલોમીટર", "km", aka = "kilometer,kms,kilo meter", pop = 5)
        u("mi", L, 1609.344, "mile", "मील", "માઇલ", "mi", aka = "miles,meel", pop = 4)
        u("nmi", L, 1852.0, "nautical mile", "समुद्री मील", "દરિયાઈ માઇલ", "NM", aka = "nautical miles,nmi")
        u("league", L, 4828.032, "league", "लीग", "લીગ", "lea")
        u("sft", L, 1200.0 / 3937.0, "US survey foot", "", "", "", "US", "survey foot")
        u("au", L, 1.495978707e11, "astronomical unit (Sun–Earth)", "खगोलीय इकाई", "ખગોળીય એકમ", "AU", aka = "astronomical unit")
        u("ly", L, 9.4607304725808e15, "light-year", "प्रकाश-वर्ष", "પ્રકાશ-વર્ષ", "ly", aka = "light year,lightyear,prakash varsh")
        u("pc", L, 3.0856775814913673e16, "parsec", "पारसेक", "પારસેક", "pc")
        // India
        u("angul", L, 0.01905, "angul (finger)", "अंगुल", "આંગળ", "", "IN", "angula,ungal", "lagbhag ¾ inch")
        u("balisht", L, 0.2286, "balisht (span)", "बालिश्त / बित्ता", "વેંત", "", "IN", "span,bitta,vent,bilsht", "lagbhag 9 inch")
        u("haath", L, 0.4572, "haath (cubit)", "हाथ", "હાથ", "", "IN", "hath,haat,cubit", "18 inch")
        u("kos", L, 3218.69, "kos", "कोस", "ગાઉ (કોસ)", "", "IN", "koss,gau,gaon", "lagbhag 2 mile (jagah pramane badle)")
        u("yojan", L, 12874.75, "yojan", "योजन", "યોજન", "", "IN", "yojana", "lagbhag 8 mile (prachin)")
        // China / HK
        u("cun", L, 1.0 / 30, "cun 寸", "", "", "寸", "CN", "tsun")
        u("chi", L, 1.0 / 3, "chi 尺", "", "", "尺", "CN", "chinese foot")
        u("zhang", L, 10.0 / 3, "zhang 丈", "", "", "丈", "CN")
        u("li", L, 500.0, "li 里", "", "", "里", "CN", "chinese mile")
        u("chek", L, 0.371475, "chek 尺 (Hong Kong)", "", "", "", "HK", "hong kong foot")
        // Japan / Korea
        u("sun", L, 10.0 / 330, "sun 寸", "", "", "寸", "JP", "japanese inch")
        u("shaku", L, 10.0 / 33, "shaku 尺", "", "", "尺", "JP", "japanese foot")
        u("ken", L, 60.0 / 33, "ken 間", "", "", "間", "JP")
        u("ri_jp", L, 12960.0 * 10 / 33, "ri 里 (Japan)", "", "", "里", "JP", "japanese ri")
        u("ja", L, 10.0 / 33, "ja 자 (Korea)", "", "", "자", "KR")
        // Russia
        u("vershok", L, 0.04445, "vershok вершок", "", "", "", "RU")
        u("arshin", L, 0.7112, "arshin аршин", "", "", "", "RU")
        u("sazhen", L, 2.1336, "sazhen сажень", "", "", "", "RU")
        u("verst", L, 1066.8, "verst верста", "", "", "", "RU", "versta")
        // Europe
        u("fuss", L, 0.31385, "Fuß (Prussian foot)", "", "", "", "DE", "fuss", "Prussia, lagbhag")
        u("elle", L, 0.66694, "Elle (Prussian)", "", "", "", "DE", note = "lagbhag")
        u("meile_de", L, 7532.5, "Meile (Prussian mile)", "", "", "", "DE", "meile", "lagbhag")
        u("pied", L, 0.32484, "pied du roi", "", "", "", "FR", "pied", "purani France")
        u("toise", L, 1.949, "toise", "", "", "", "FR")
        u("lieue", L, 4444.8, "lieue", "", "", "", "FR", "league fr", "lagbhag")
        u("vara", L, 0.8359, "vara (Castile)", "", "", "", "ES", note = "lagbhag")
        u("legua", L, 5572.7, "legua (Spain)", "", "", "", "ES", note = "lagbhag")
        u("braca", L, 2.2, "braça", "", "", "", "PT", "braca")
        u("mil_se", L, 10000.0, "mil (Scandinavian mile)", "", "", "", "SE", "swedish mile,norwegian mile")
        u("aln", L, 0.5938, "aln (Sweden)", "", "", "", "SE")
        u("arsin", L, 0.68, "arşın (Ottoman)", "", "", "", "TR", "arsin", "lagbhag")
        u("farsakh", L, 5600.0, "farsakh / parasang", "फ़रसख़", "", "", "IR", "parasang,farsang", "lagbhag")
        u("wa", L, 2.0, "wa วา (Thailand)", "", "", "", "TH")
        u("sok", L, 0.5, "sok ศอก (Thailand)", "", "", "", "TH")
        u("depa", L, 1.8288, "depa (Malay)", "", "", "", "MY")
        // ancient
        u("cubit_eg", L, 0.5236, "royal cubit (Egypt)", "", "", "", "XX", "egyptian cubit")
        u("roman_mile", L, 1480.0, "Roman mile", "", "", "", "XX", "mille passus")
        u("stadion", L, 185.0, "stadion (Greek)", "", "", "", "XX", "stade", "lagbhag")

        ref(L, 1e-10, "atom", "परमाणु", "પરમાણુ")
        ref(L, 1e-7, "virus", "वायरस", "વાયરસ")
        ref(L, 7e-5, "human hair", "बाल की मोटाई", "વાળની જાડાઈ")
        ref(L, 0.005, "ant", "चींटी", "કીડી")
        ref(L, 0.0856, "ATM card", "ATM कार्ड", "ATM કાર્ડ")
        ref(L, 1.7, "person", "इंसान", "માણસ")
        ref(L, 3.0, "one floor", "एक मंज़िल", "એક માળ")
        ref(L, 12.0, "bus", "बस", "બસ")
        ref(L, 20.12, "cricket pitch", "क्रिकेट पिच", "ક્રિકેટ પિચ")
        ref(L, 105.0, "football ground", "फुटबॉल मैदान", "ફૂટબોલ મેદાન")
        ref(L, 8849.0, "Everest", "एवरेस्ट", "એવરેસ્ટ")
        ref(L, 1.2742e7, "Earth", "पृथ्वी", "પૃથ્વી")
        ref(L, 3.844e8, "Earth → Moon", "पृथ्वी → चाँद", "પૃથ્વી → ચંદ્ર")

        // ======================= AREA (base: m²) =======================
        val A = "area"
        u("mm2", A, 1e-6, "sq mm", "वर्ग मिमी", "ચો. મિમી", "mm²", aka = "mm2,square millimeter")
        u("cm2", A, 1e-4, "sq cm", "वर्ग सेमी", "ચો. સેમી", "cm²", aka = "cm2,square centimeter")
        u("in2", A, 6.4516e-4, "sq inch", "वर्ग इंच", "ચો. ઇંચ", "in²", aka = "sq in,square inch,in2,sqin")
        u("ft2", A, 0.09290304, "sq ft", "वर्ग फुट", "ચો. ફૂટ", "ft²", aka = "sqft,sq feet,square feet,square foot,ft2,varg fut", pop = 6)
        u("yd2", A, 0.83612736, "sq yard (gaj)", "वर्ग गज़", "ચો. વાર", "yd²", aka = "sq yd,square yard,gaj,var,vaar,sq gaj,yd2", pop = 5)
        u("m2", A, 1.0, "sq metre", "वर्ग मीटर", "ચો. મીટર", "m²", aka = "sqm,sq m,square meter,square metre,m2", pop = 6)
        u("are", A, 100.0, "are", "आर", "આર", "a")
        u("ha", A, 1e4, "hectare", "हेक्टेयर", "હેક્ટર", "ha", aka = "hectares,hectar", pop = 5)
        u("acre", A, 4046.8564224, "acre", "एकड़", "એકર", "ac", aka = "acres,ekad,ekar", pop = 6)
        u("km2", A, 1e6, "sq km", "वर्ग किमी", "ચો. કિમી", "km²", aka = "sq km,square kilometer,km2", pop = 3)
        u("mi2", A, 2589988.110336, "sq mile", "वर्ग मील", "ચો. માઇલ", "mi²", aka = "sq mi,square mile")
        // India (state wise)
        u("guntha", A, 101.17141056, "guntha (Gujarat / Maharashtra)", "गुंठा", "ગુંઠા", "", "IN", "gunta,guntha,gunda", "1089 sq ft", pop = 5)
        u("vigha_gj", A, 1618.7425856, "vigha (Gujarat)", "बीघा (गुजरात)", "વીઘા (ગુજરાત)", "", "IN", "vigha,veegha,viga,vigha gujarat", "16 guntha = 17,424 sq ft", pop = 6)
        u("bigha_up", A, 2508.38, "bigha (Uttar Pradesh)", "बीघा (उत्तर प्रदेश)", "બીઘા (ઉત્તર પ્રદેશ)", "", "IN", "bigha,bigha up", "lagbhag 27,000 sq ft (jile pramane badle)", pop = 3)
        u("bigha_rj", A, 2529.29, "bigha pakka (Rajasthan)", "बीघा पक्का (राजस्थान)", "બીઘા પાકો (રાજસ્થાન)", "", "IN", "bigha rajasthan,pakka bigha", "27,225 sq ft")
        u("bigha_rjk", A, 1618.74, "bigha kachcha (Rajasthan)", "बीघा कच्चा (राजस्थान)", "બીઘા કાચો (રાજસ્થાન)", "", "IN", "kachcha bigha", "17,424 sq ft")
        u("bigha_bih", A, 2529.0, "bigha (Bihar)", "बीघा (बिहार)", "બીઘા (બિહાર)", "", "IN", "bigha bihar", "lagbhag 27,220 sq ft")
        u("bigha_wb", A, 1337.8, "bigha (West Bengal / Assam)", "बीघा (बंगाल / असम)", "બીઘા (બંગાળ / આસામ)", "", "IN", "bigha bengal,bigha assam", "14,400 sq ft")
        u("bigha_mp", A, 1114.84, "bigha (Madhya Pradesh)", "बीघा (मध्य प्रदेश)", "બીઘા (મધ્ય પ્રદેશ)", "", "IN", "bigha mp", "lagbhag 12,000 sq ft")
        u("bigha_hp", A, 809.37, "bigha (Himachal)", "बीघा (हिमाचल)", "બીઘા (હિમાચલ)", "", "IN", "bigha himachal", "8,712 sq ft")
        u("bigha_pb", A, 842.6, "bigha (Punjab / Haryana)", "बीघा (पंजाब / हरियाणा)", "બીઘા (પંજાબ / હરિયાણા)", "", "IN", "bigha punjab,bigha haryana", "lagbhag 9,070 sq ft")
        u("biswa", A, 125.42, "biswa (UP)", "बिस्वा", "બિસ્વા", "", "IN", "biswa", "1/20 bigha (UP)")
        u("kanal", A, 505.857, "kanal", "कनाल", "કનાલ", "", "IN", "kanal", "Punjab, Haryana, J&K, Pakistan: 5,445 sq ft", pop = 3)
        u("marla", A, 25.2929, "marla", "मरला", "મરલા", "", "IN", "marla", "272.25 sq ft (kahi 225)", pop = 3)
        u("murabba", A, 101171.41, "murabba / killa ×25", "मुरब्बा", "મુરબ્બા", "", "IN", "murabba", "25 acre")
        u("cent", A, 40.4686, "cent (Kerala / TN)", "सेंट", "સેન્ટ", "", "IN", "cent,cents", "435.6 sq ft = 1/100 acre")
        u("ground", A, 222.967, "ground (Tamil Nadu)", "ग्राउंड", "ગ્રાઉન્ડ", "", "IN", "ground", "2,400 sq ft")
        u("ankanam", A, 6.689, "ankanam (Andhra / Telangana)", "अंकनम", "અંકનમ", "", "IN", "ankanam", "72 sq ft")
        u("katha_bih", A, 126.45, "katha (Bihar)", "कट्ठा (बिहार)", "કઠ્ઠા (બિહાર)", "", "IN", "katha,kattha", "lagbhag 1,361 sq ft")
        u("katha_wb", A, 66.89, "katha / kottah (Bengal)", "कट्ठा (बंगाल)", "કઠ્ઠા (બંગાળ)", "", "IN", "kottah,katha bengal", "720 sq ft")
        u("dhur", A, 6.32, "dhur (Bihar)", "धुर", "ધુર", "", "IN", note = "lagbhag 68 sq ft")
        // world
        u("ropani", A, 508.737, "ropani", "रोपनी", "રોપની", "", "NP", "ropani", "Nepal, 5,476 sq ft")
        u("aana_np", A, 31.796, "aana (Nepal)", "आना", "આના", "", "NP", "aana", "342.25 sq ft")
        u("bigha_np", A, 6772.63, "bigha (Nepal)", "बीघा (नेपाल)", "બીઘા (નેપાળ)", "", "NP", "nepal bigha")
        u("kattha_np", A, 338.63, "kattha (Nepal)", "कट्ठा (नेपाल)", "", "", "NP")
        u("shotok", A, 40.4686, "shotok / decimal (Bangladesh)", "शतक", "", "", "BD", "shotok,decimal,satak")
        u("katha_bd", A, 66.89, "katha (Bangladesh)", "", "", "", "BD")
        u("perch", A, 25.29285264, "perch (Sri Lanka / UK)", "", "", "", "LK", "sq rod")
        u("rood", A, 1011.7141056, "rood", "", "", "", "GB")
        u("mu", A, 2000.0 / 3, "mu 亩", "", "", "亩", "CN", "mou,chinese mu")
        u("qing", A, 200000.0 / 3, "qing 顷", "", "", "顷", "CN")
        u("tsubo", A, 400.0 / 121, "tsubo 坪", "", "", "坪", "JP", "tsubo")
        u("jo", A, 1.62, "jō 畳 (tatami)", "", "", "畳", "JP", "tatami,jo", "jagah pramane 1.5–1.8 m²")
        u("tan_jp", A, 120000.0 / 121, "tan 反", "", "", "反", "JP")
        u("cho_jp", A, 1200000.0 / 121, "chō 町", "", "", "町", "JP", "cho")
        u("pyeong", A, 400.0 / 121, "pyeong 평", "", "", "평", "KR", "pyong")
        u("sotka", A, 100.0, "sotka сотка", "", "", "", "RU")
        u("desyatina", A, 10925.4, "desyatina десятина", "", "", "", "RU")
        u("morgen", A, 2553.22, "Morgen (Prussian)", "", "", "", "DE", note = "lagbhag")
        u("arpent", A, 3418.89, "arpent", "", "", "", "FR")
        u("fanega", A, 6439.56, "fanega (Spain)", "", "", "", "ES", note = "lagbhag")
        u("alq_sp", A, 24200.0, "alqueire paulista", "", "", "", "BR", "alqueire")
        u("donum", A, 1000.0, "dönüm / dunam", "", "", "", "TR", "donum,dunam,dunum")
        u("feddan", A, 4200.83, "feddan", "", "", "", "EG")
        u("rai", A, 1600.0, "rai ไร่", "", "", "", "TH")
        u("ngan", A, 400.0, "ngan งาน", "", "", "", "TH")

        ref(A, 0.06237, "A4 sheet", "A4 कागज़", "A4 કાગળ")
        ref(A, 3.6, "double bed", "डबल बेड", "ડબલ બેડ")
        ref(A, 12.5, "car parking", "कार पार्किंग", "કાર પાર્કિંગ")
        ref(A, 260.87, "tennis court", "टेनिस कोर्ट", "ટેનિસ કોર્ટ")
        ref(A, 420.0, "basketball court", "बास्केटबॉल कोर्ट", "બાસ્કેટબોલ કોર્ટ")
        ref(A, 7140.0, "football ground", "फुटबॉल मैदान", "ફૂટબોલ મેદાન")
        ref(A, 1.0e8, "a small city (100 km²)", "छोटा शहर", "નાનું શહેર")

        // ======================= MASS (base: kg) =======================
        val M = "mass"
        u("electron", M, 9.1093837015e-31, "electron mass", "इलेक्ट्रॉन", "ઇલેક્ટ્રોન", "mₑ")
        u("dalton", M, 1.6605390666e-27, "dalton (atomic mass unit)", "डाल्टन", "ડાલ્ટન", "Da", aka = "amu,u")
        u("ug", M, 1e-9, "microgram", "माइक्रोग्राम", "માઇક્રોગ્રામ", "µg", aka = "mcg,ug")
        u("mg", M, 1e-6, "milligram", "मिलीग्राम", "મિલીગ્રામ", "mg", pop = 3)
        u("grain", M, 6.479891e-5, "grain", "ग्रेन", "ગ્રેન", "gr")
        u("ct", M, 2e-4, "carat (gem)", "कैरेट", "કેરેટ", "ct", aka = "carat,carats", pop = 2)
        u("g", M, 1e-3, "gram", "ग्राम", "ગ્રામ", "g", aka = "gram,grams,gm,gms,gr", pop = 6)
        u("oz", M, 0.028349523125, "ounce", "औंस", "ઔંસ", "oz", aka = "ounce,ounces", pop = 3)
        u("ozt", M, 0.0311034768, "troy ounce (gold)", "ट्रॉय औंस", "ટ્રોય ઔંસ", "oz t", aka = "troy ounce,ozt", pop = 2)
        u("lb", M, 0.45359237, "pound", "पाउंड", "પાઉન્ડ", "lb", aka = "pound,pounds,lbs", pop = 4)
        u("kg", M, 1.0, "kilogram", "किलो", "કિલો", "kg", aka = "kilo,kilos,kilogram,kgs", pop = 6)
        u("stone", M, 6.35029318, "stone", "स्टोन", "સ્ટોન", "st", "GB")
        u("cwt_us", M, 45.359237, "hundredweight (US)", "", "", "cwt", "US")
        u("cwt_uk", M, 50.80234544, "hundredweight (UK)", "", "", "cwt", "GB")
        u("quintal", M, 100.0, "quintal", "क्विंटल", "ક્વિન્ટલ", "q", aka = "quintal,kwintal,qtl", pop = 5)
        u("t", M, 1000.0, "tonne (metric ton)", "टन", "ટન", "t", aka = "ton,tonne,tons,metric ton", pop = 5)
        u("ton_us", M, 907.18474, "short ton (US)", "", "", "", "US", "short ton")
        u("ton_uk", M, 1016.0469088, "long ton (UK)", "", "", "", "GB", "long ton")
        // India
        u("ratti", M, 1.21497956e-4, "ratti", "रत्ती", "રતી", "", "IN", "ratti,rati", "1/96 tola")
        u("masha", M, 9.7198365e-4, "masha", "माशा", "માસા", "", "IN", "masha,maasha", "1/12 tola")
        u("tola", M, 0.0116638038, "tola", "तोला", "તોલા", "", "IN", "tola,tolaa,tole", "11.664 g", pop = 5)
        u("chhatak", M, 0.0583190, "chhatak", "छटांक", "છટાંક", "", "IN", "chhatak,chatak", "1/16 ser")
        u("pav", M, 0.2332760, "pav (¼ ser)", "पाव", "પાશેર", "", "IN", "pav,pao,paav,pasher")
        u("ser", M, 0.93310, "ser (seer)", "सेर", "શેર", "", "IN", "seer,ser,sher", "80 tola")
        u("man_gj", M, 20.0, "man (Gujarat)", "मन (गुजरात)", "મણ (ગુજરાત)", "", "IN", "man,mann,maan,man gujarat", "Gujarat bazaar: 20 kg", pop = 6)
        u("man_40", M, 40.0, "man (40 kg)", "मन (40 किलो)", "મણ (40 કિલો)", "", "IN", "man 40,north man", "uttar bharat bazaar")
        u("maund", M, 37.3242, "maund (British India)", "मन (पुराना)", "મણ (જૂનો)", "", "IN", "maund", "40 ser")
        // China / HK / Japan / Korea
        u("liang", M, 0.05, "liang 两", "", "", "两", "CN")
        u("jin", M, 0.5, "jin 斤 (catty)", "", "", "斤", "CN", "jin,chinese catty")
        u("dan", M, 50.0, "dan 担", "", "", "担", "CN")
        u("tael", M, 0.0377994, "tael 兩 (Hong Kong)", "", "", "", "HK", "tael")
        u("catty", M, 0.60478982, "catty 斤 (Hong Kong)", "", "", "", "HK", "catty,kati hk")
        u("picul", M, 60.478982, "picul 擔", "", "", "", "HK")
        u("momme", M, 0.00375, "monme 匁", "", "", "匁", "JP", "momme")
        u("kin_jp", M, 0.6, "kin 斤", "", "", "斤", "JP", "kin")
        u("kan", M, 3.75, "kan 貫", "", "", "貫", "JP", "kanme")
        u("don", M, 0.00375, "don 돈", "", "", "돈", "KR")
        u("geun", M, 0.6, "geun 근", "", "", "근", "KR")
        u("gwan", M, 3.75, "gwan 관", "", "", "관", "KR")
        // Russia / Europe
        u("zolotnik", M, 0.0042658, "zolotnik золотник", "", "", "", "RU")
        u("funt", M, 0.40951718, "funt фунт", "", "", "", "RU")
        u("pud", M, 16.3804964, "pood пуд", "", "", "", "RU", "pood,pud")
        u("berkovets", M, 163.804964, "berkovets берковец", "", "", "", "RU")
        u("pfund", M, 0.5, "Pfund", "", "", "", "DE", "pfund")
        u("zentner", M, 50.0, "Zentner", "", "", "", "DE")
        u("livre", M, 0.48951, "livre", "", "", "", "FR")
        u("arroba_es", M, 11.502, "arroba (Spain)", "", "", "", "ES", "arroba")
        u("arroba_br", M, 15.0, "arroba (Brazil)", "", "", "", "BR")
        u("okka", M, 1.2829, "okka (Ottoman)", "", "", "", "TR", "oka,oke")
        u("kantar", M, 56.45, "kantar", "", "", "", "TR", "qintar")
        // Middle East / Asia
        u("mithqal", M, 0.004608, "mithqal / mesghal", "मिसक़ाल", "", "", "IR", "mithqal,mesghal,misqal")
        u("dirham_w", M, 0.0032, "dirham (weight)", "", "", "", "AE", "dirham weight", "lagbhag")
        u("baht_w", M, 0.015244, "baht (gold weight)", "", "", "", "TH", "baht gold")
        u("hap", M, 60.0, "hap หาบ", "", "", "", "TH")
        u("kati_my", M, 0.60479, "kati (Malaysia)", "", "", "", "MY", "kati")
        u("tahil", M, 0.0377994, "tahil", "", "", "", "MY")
        u("viss", M, 1.63293, "viss (Myanmar)", "", "", "", "MM", "peiktha")
        u("tical", M, 0.0163293, "tical / kyat", "", "", "", "MM", "kyat weight")
        u("talent", M, 26.0, "talent (Greek)", "", "", "", "XX", note = "lagbhag")
        u("mina", M, 0.431, "mina", "", "", "", "XX", note = "lagbhag")

        ref(M, 2.5e-5, "grain of rice", "चावल का दाना", "ચોખાનો દાણો")
        ref(M, 0.005, "₹5 coin", "₹5 सिक्का", "₹5 સિક્કો")
        ref(M, 0.06, "egg", "अंडा", "ઈંડું")
        ref(M, 3.0, "brick", "ईंट", "ઈંટ")
        ref(M, 50.0, "cement bag", "सीमेंट बोरी", "સિમેન્ટ થેલી")
        ref(M, 70.0, "person", "इंसान", "માણસ")
        ref(M, 1200.0, "car", "कार", "કાર")
        ref(M, 5000.0, "elephant", "हाथी", "હાથી")
        ref(M, 25000.0, "loaded truck", "भरा ट्रक", "ભરેલી ટ્રક")
        ref(M, 400000.0, "Boeing 747", "बोइंग 747 विमान", "બોઇંગ 747 વિમાન")

        // ======================= VOLUME (base: m³) =======================
        val V = "vol"
        u("ul", V, 1e-9, "microlitre", "माइक्रोलीटर", "માઇક્રોલિટર", "µL", aka = "ul,microliter")
        u("ml", V, 1e-6, "millilitre", "मिलीलीटर", "મિલીલિટર", "mL", aka = "ml,milliliter,cc", pop = 5)
        u("tsp", V, 4.92892159375e-6, "teaspoon", "छोटी चम्मच", "નાની ચમચી", "tsp", aka = "teaspoon,chammach")
        u("tbsp", V, 1.478676478125e-5, "tablespoon", "बड़ी चम्मच", "મોટી ચમચી", "tbsp", aka = "tablespoon")
        u("floz", V, 2.95735295625e-5, "fluid ounce (US)", "", "", "fl oz", "US", "fl oz,fluid ounce")
        u("floz_uk", V, 2.84130625e-5, "fluid ounce (UK)", "", "", "fl oz", "GB")
        u("in3", V, 1.6387064e-5, "cubic inch", "घन इंच", "ઘન ઇંચ", "in³", aka = "cu in,cubic inch,in3")
        u("cup", V, 2.5e-4, "cup (metric)", "कप", "કપ", "cup", aka = "cup,cups")
        u("cup_us", V, 2.365882365e-4, "cup (US)", "", "", "cup", "US")
        u("katori", V, 1.5e-4, "katori", "कटोरी", "વાટકી", "", "IN", "katori,vatki", "lagbhag 150 mL")
        u("pint_us", V, 4.73176473e-4, "pint (US)", "", "", "pt", "US", "pint")
        u("pint_uk", V, 5.6826125e-4, "pint (UK)", "", "", "pt", "GB")
        u("l", V, 1e-3, "litre", "लीटर", "લિટર", "L", aka = "liter,liters,litres,ltr,lit", pop = 6)
        u("gal_us", V, 3.785411784e-3, "gallon (US)", "गैलन (US)", "ગેલન (US)", "gal", "US", "gallon,gallons,us gallon", pop = 3)
        u("gal_uk", V, 4.54609e-3, "gallon (UK)", "गैलन (UK)", "ગેલન (UK)", "gal", "GB", "uk gallon,imperial gallon")
        u("ft3", V, 0.028316846592, "cubic foot (cft)", "घन फुट", "ઘન ફૂટ", "ft³", aka = "cft,cu ft,cubic feet,cubic foot,ft3", pop = 6)
        u("bbl", V, 0.158987294928, "barrel (oil)", "बैरल", "બેરલ", "bbl", aka = "barrel,barrels")
        u("yd3", V, 0.764554857984, "cubic yard", "घन गज़", "ઘન વાર", "yd³", aka = "cu yd,yd3")
        u("m3", V, 1.0, "cubic metre", "घन मीटर", "ઘન મીટર", "m³", aka = "cbm,cu m,cubic meter,m3", pop = 6)
        u("brass", V, 2.8316846592, "brass (100 cft)", "ब्रास", "બ્રાસ", "", "IN", "brass", "ret / gitti", pop = 5)
        u("acft", V, 1233.48183754752, "acre-foot", "एकड़-फुट", "", "ac·ft", aka = "acre foot")
        u("tmc", V, 2.8316846592e7, "TMC (dam water)", "TMC", "TMC", "", "IN", "tmc,tmcft", "thousand million cubic feet")
        u("go", V, 1.803907e-4, "gō 合", "", "", "合", "JP")
        u("sho", V, 1.803907e-3, "shō 升", "", "", "升", "JP")
        u("koku", V, 0.1803907, "koku 石", "", "", "石", "JP")
        u("sheng", V, 1e-3, "sheng 升", "", "", "升", "CN")
        u("dou", V, 0.01, "dou 斗", "", "", "斗", "CN")
        u("doe", V, 1.8e-3, "doe 되", "", "", "되", "KR")
        u("vedro", V, 0.01229941, "vedro ведро", "", "", "", "RU")
        u("bochka", V, 0.4919764, "bochka бочка", "", "", "", "RU")
        u("bushel", V, 0.03523907016688, "bushel (US)", "", "", "bu", "US")
        u("amphora", V, 0.0262, "amphora (Roman)", "", "", "", "XX", note = "lagbhag")

        ref(V, 5e-8, "drop", "बूँद", "ટીપું")
        ref(V, 2.5e-4, "glass of water", "पानी का गिलास", "પાણીનો ગ્લાસ")
        ref(V, 0.015, "bucket", "बाल्टी", "ડોલ")
        ref(V, 0.2, "oil drum", "ड्रम", "ડ્રમ")
        ref(V, 10.0, "water tanker", "पानी का टैंकर", "પાણીનું ટેન્કર")
        ref(V, 2500.0, "Olympic pool", "ओलंपिक तालाब", "ઓલિમ્પિક સ્વિમિંગ પૂલ")

        // ======================= TEMPERATURE (base: kelvin) =======================
        val T = "temp"
        u("c", T, 1.0, "Celsius", "सेल्सियस", "સેલ્સિયસ", "°C", aka = "celsius,centigrade,degree c,deg c,°c,c", off = 273.15, pop = 6)
        u("f", T, 5.0 / 9, "Fahrenheit", "फ़ारेनहाइट", "ફેરનહીટ", "°F", aka = "fahrenheit,degree f,deg f,°f,f", off = 459.67 * 5 / 9, pop = 5)
        u("k", T, 1.0, "kelvin", "केल्विन", "કેલ્વિન", "K", aka = "kelvin", pop = 4)
        u("ra", T, 5.0 / 9, "Rankine", "रैंकिन", "રેન્કિન", "°R", aka = "rankine")
        u("re", T, 1.25, "Réaumur", "रोमर", "રોમર", "°Ré", aka = "reaumur", off = 273.15)

        // ======================= TIME (base: second) =======================
        val TI = "time"
        u("ns", TI, 1e-9, "nanosecond", "नैनोसेकंड", "નેનોસેકન્ડ", "ns")
        u("us", TI, 1e-6, "microsecond", "माइक्रोसेकंड", "માઇક્રોસેકન્ડ", "µs")
        u("ms", TI, 1e-3, "millisecond", "मिलीसेकंड", "મિલીસેકન્ડ", "ms")
        u("s", TI, 1.0, "second", "सेकंड", "સેકન્ડ", "s", aka = "sec,second,seconds", pop = 5)
        u("pal", TI, 24.0, "pal (vipal ×60)", "पल", "પળ", "", "IN", "pal", "24 second")
        u("min", TI, 60.0, "minute", "मिनट", "મિનિટ", "min", aka = "minute,minutes,mins", pop = 5)
        u("ghadi", TI, 1440.0, "ghadi (ghatika)", "घड़ी", "ઘડી", "", "IN", "ghadi,ghatika", "24 minute")
        u("muhurt", TI, 2880.0, "muhurta", "मुहूर्त", "મુહૂર્ત", "", "IN", "muhurta,muhurt", "48 minute")
        u("h", TI, 3600.0, "hour", "घंटा", "કલાક", "h", aka = "hour,hours,hr,hrs,ghanta,kalak", pop = 6)
        u("shichen", TI, 7200.0, "shíchen 时辰", "", "", "", "CN")
        u("prahar", TI, 10800.0, "prahar", "प्रहर", "પહોર", "", "IN", "prahar,pahar,pahor", "3 ghanta")
        u("day", TI, 86400.0, "day", "दिन", "દિવસ", "d", aka = "day,days,din,divas", pop = 6)
        u("week", TI, 604800.0, "week", "हफ़्ता", "અઠવાડિયું", "wk", aka = "week,weeks,hafta", pop = 4)
        u("month", TI, 2629746.0, "month (average)", "महीना", "મહિનો", "mo", aka = "month,months,mahina", pop = 5)
        u("year", TI, 31556952.0, "year", "साल", "વર્ષ", "yr", aka = "year,years,saal,varas", pop = 5)
        u("decade", TI, 315569520.0, "decade", "दशक", "દાયકો", "", aka = "decade,dashak")
        u("century", TI, 3155695200.0, "century", "सदी", "સદી", "", aka = "century,sadi")
        u("millennium", TI, 31556952000.0, "millennium", "सहस्राब्दी", "સહસ્રાબ્દી")

        // ======================= SPEED (base: m/s) =======================
        val SP = "speed"
        u("mps", SP, 1.0, "metre / second", "मीटर/सेकंड", "મીટર/સેકન્ડ", "m/s", aka = "m/s,mps", pop = 4)
        u("kmh", SP, 1 / 3.6, "km / hour", "किमी/घंटा", "કિમી/કલાક", "km/h", aka = "kmph,km/h,kmh,kph", pop = 6)
        u("mph", SP, 0.44704, "mile / hour", "मील/घंटा", "માઇલ/કલાક", "mph", aka = "mph,miles per hour", pop = 4)
        u("fps", SP, 0.3048, "foot / second", "फुट/सेकंड", "ફૂટ/સેકન્ડ", "ft/s", aka = "ft/s,fps")
        u("knot", SP, 1852.0 / 3600, "knot", "नॉट", "નોટ", "kn", aka = "knot,knots,kt")
        u("mach", SP, 343.0, "Mach (sound)", "मैक (आवाज़)", "મેક (અવાજ)", "Ma", aka = "mach,speed of sound", note = "20°C hava ma")
        u("c_light", SP, 299792458.0, "speed of light", "प्रकाश की गति", "પ્રકાશની ઝડપ", "c", aka = "speed of light,light speed")

        // ======================= PRESSURE (base: Pa) =======================
        val P = "press"
        u("pa", P, 1.0, "pascal", "पास्कल", "પાસ્કલ", "Pa", aka = "pascal", pop = 4)
        u("hpa", P, 100.0, "hectopascal / mbar", "", "", "hPa", aka = "hpa,mbar,millibar")
        u("kpa", P, 1000.0, "kilopascal", "", "", "kPa", aka = "kpa", pop = 3)
        u("mmhg", P, 133.322387415, "mm of mercury", "मिमी पारा", "", "mmHg", aka = "mmhg,torr", pop = 3)
        u("inh2o", P, 249.08891, "inch of water", "", "", "inH₂O")
        u("inhg", P, 3386.389, "inch of mercury", "", "", "inHg")
        u("psi", P, 6894.757293168, "psi", "PSI", "PSI", "psi", aka = "psi,lb/in2", pop = 5)
        u("mh2o", P, 9806.65, "metre of water", "मीटर पानी", "મીટર પાણી", "mH₂O", aka = "meter water")
        u("ksc", P, 98066.5, "kg / cm²", "किग्रा/सेमी²", "કિગ્રા/સેમી²", "kg/cm²", aka = "ksc,kg/cm2,kgf/cm2", pop = 5)
        u("bar", P, 1e5, "bar", "बार", "બાર", "bar", aka = "bar", pop = 5)
        u("atm", P, 101325.0, "atmosphere", "वायुमंडल", "વાતાવરણ", "atm", aka = "atm,atmosphere", pop = 3)
        u("mpa", P, 1e6, "MPa = N/mm²", "", "", "MPa", aka = "mpa,n/mm2", pop = 3)

        // ======================= ENERGY (base: J) =======================
        val E = "energy"
        u("ev", E, 1.602176634e-19, "electron-volt", "इलेक्ट्रॉन-वोल्ट", "ઇલેક્ટ્રોન-વોલ્ટ", "eV", aka = "ev,electron volt")
        u("erg", E, 1e-7, "erg", "अर्ग", "અર્ગ", "erg")
        u("j", E, 1.0, "joule", "जूल", "જૂલ", "J", aka = "joule,joules", pop = 4)
        u("ftlb", E, 1.3558179483314004, "foot-pound", "", "", "ft·lbf")
        u("cal", E, 4.184, "calorie", "कैलोरी", "કેલરી", "cal", aka = "calorie")
        u("kj", E, 1000.0, "kilojoule", "", "", "kJ", aka = "kj")
        u("btu", E, 1055.05585262, "BTU", "BTU", "BTU", "BTU", aka = "btu", pop = 3)
        u("kcal", E, 4184.0, "kilocalorie (food Calorie)", "किलो कैलोरी", "કિલો કેલરી", "kcal", aka = "kcal,kilocalorie,food calorie", pop = 4)
        u("wh", E, 3600.0, "watt-hour", "वाट-घंटा", "વોટ-કલાક", "Wh", aka = "wh")
        u("mj", E, 1e6, "megajoule", "", "", "MJ", aka = "mj")
        u("kwh", E, 3.6e6, "kWh (bijli ka unit)", "यूनिट (kWh)", "યુનિટ (kWh)", "kWh", aka = "kwh,unit,units,bijli unit", pop = 6)
        u("therm", E, 1.05505585262e8, "therm", "", "", "thm")
        u("ttnt", E, 4.184e9, "ton of TNT", "टन TNT", "", "tTNT", aka = "ton tnt")
        u("toe", E, 4.1868e10, "tonne of oil equivalent", "", "", "toe")

        // ======================= POWER (base: W) =======================
        val PW = "power"
        u("mw_", PW, 1e-3, "milliwatt", "", "", "mW")
        u("w", PW, 1.0, "watt", "वाट", "વોટ", "W", aka = "watt,watts", pop = 5)
        u("kcalh", PW, 1.163, "kcal / hour", "", "", "kcal/h")
        u("btuh", PW, 0.29307107, "BTU / hour", "", "", "BTU/h", aka = "btu/h,btuh")
        u("hp_m", PW, 735.49875, "horsepower (metric, PS)", "हॉर्सपावर (PS)", "હોર્સપાવર (PS)", "PS", aka = "ps,metric hp", pop = 3)
        u("hp", PW, 745.69987158227, "horsepower (HP)", "हॉर्सपावर", "હોર્સપાવર", "hp", aka = "hp,horsepower,bhp", pop = 5)
        u("kw", PW, 1000.0, "kilowatt", "किलोवाट", "કિલોવોટ", "kW", aka = "kw,kilowatt", pop = 5)
        u("tr", PW, 3516.8528420667, "ton (AC / refrigeration)", "टन (AC)", "ટન (AC)", "TR", aka = "ac ton,tr,ton ac", pop = 4)
        u("mw", PW, 1e6, "megawatt", "मेगावाट", "મેગાવોટ", "MW", aka = "megawatt", pop = 3)
        u("gw", PW, 1e9, "gigawatt", "गीगावाट", "ગીગાવોટ", "GW", aka = "gigawatt")

        // ======================= NUMBERS (base: 1) =======================
        val N = "count"
        u("one", N, 1.0, "one", "एक", "એક", "", aka = "one,ek,nag,pcs,piece", pop = 4)
        u("dozen", N, 12.0, "dozen", "दर्जन", "ડઝન", "", aka = "dozen,darjan", pop = 4)
        u("score", N, 20.0, "score (kodi)", "कोड़ी", "કોડી", "", aka = "score,kodi")
        u("gross", N, 144.0, "gross", "ग्रोस", "ગ્રોસ", "")
        u("hundred", N, 100.0, "hundred", "सौ", "સો", "", aka = "hundred,sau,so")
        u("hazaar", N, 1e3, "thousand", "हज़ार", "હજાર", "", aka = "thousand,hazaar,hajar,k", pop = 5)
        u("wan", N, 1e4, "wan 万 / man 万", "", "", "万", "CN", "wan,man jp")
        u("lakh", N, 1e5, "lakh", "लाख", "લાખ", "", "IN", "lakh,lac,lakhs", pop = 6)
        u("million", N, 1e6, "million", "मिलियन", "મિલિયન", "", aka = "million,mn", pop = 5)
        u("crore", N, 1e7, "crore", "करोड़", "કરોડ", "", "IN", "crore,cr,karod", pop = 6)
        u("yi", N, 1e8, "yi 亿 / oku 億", "", "", "亿", "CN", "yi,oku")
        u("billion", N, 1e9, "billion", "बिलियन", "બિલિયન", "", aka = "billion,bn", pop = 5)
        u("arab", N, 1e9, "arab", "अरब", "અબજ", "", "IN", "arab,abaj", pop = 3)
        u("kharab", N, 1e11, "kharab", "खरब", "ખર્વ", "", "IN", "kharab")
        u("trillion", N, 1e12, "trillion", "ट्रिलियन", "ટ્રિલિયન", "", aka = "trillion,tn")

        // ======================= ANGLE (base: radian) =======================
        val AN = "angle"
        u("deg", AN, PI / 180, "degree", "डिग्री", "ડિગ્રી", "°", aka = "degree,degrees,deg", pop = 6)
        u("rad", AN, 1.0, "radian", "रेडियन", "રેડિયન", "rad", aka = "radian,radians", pop = 4)
        u("grad", AN, PI / 200, "gradian (gon)", "ग्रेड", "ગ્રેડ", "gon", aka = "grad,gon")
        u("arcmin", AN, PI / 10800, "arc minute", "कोणीय मिनट", "", "′", aka = "arcmin")
        u("arcsec", AN, PI / 648000, "arc second", "कोणीय सेकंड", "", "″", aka = "arcsec")
        u("turn", AN, 2 * PI, "turn (360°)", "पूरा चक्कर", "આખું ચક્કર", "", aka = "turn,revolution,rev")
        u("milang", AN, 2 * PI / 6400, "mil (NATO)", "", "", "mil", aka = "nato mil")

        // ======================= DATA (base: byte) =======================
        val D = "data"
        u("bit", D, 0.125, "bit", "बिट", "બિટ", "b", aka = "bit,bits", pop = 3)
        u("byte", D, 1.0, "byte", "बाइट", "બાઇટ", "B", aka = "byte,bytes", pop = 3)
        u("kb", D, 1e3, "kilobyte", "", "", "KB", aka = "kb,kilobyte", pop = 4)
        u("kib", D, 1024.0, "kibibyte", "", "", "KiB")
        u("mb", D, 1e6, "megabyte", "", "", "MB", aka = "mb,megabyte", pop = 5)
        u("mib", D, 1048576.0, "mebibyte", "", "", "MiB")
        u("gb", D, 1e9, "gigabyte", "", "", "GB", aka = "gb,gigabyte", pop = 5)
        u("gib", D, 1073741824.0, "gibibyte", "", "", "GiB")
        u("tb", D, 1e12, "terabyte", "", "", "TB", aka = "tb,terabyte", pop = 4)
        u("pb", D, 1e15, "petabyte", "", "", "PB")

        // ======================= FORCE (base: N) =======================
        val F = "force"
        u("dyn", F, 1e-5, "dyne", "डाइन", "ડાઇન", "dyn")
        u("n", F, 1.0, "newton", "न्यूटन", "ન્યૂટન", "N", aka = "newton,newtons", pop = 5)
        u("lbf", F, 4.4482216152605, "pound-force", "", "", "lbf", pop = 3)
        u("kgf", F, 9.80665, "kilogram-force", "किलो-बल", "કિલો-બળ", "kgf", aka = "kgf,kg force", pop = 5)
        u("kn", F, 1000.0, "kilonewton", "किलोन्यूटन", "કિલોન્યૂટન", "kN", aka = "kn", pop = 5)
        u("tf", F, 9806.65, "tonne-force", "टन-बल", "ટન-બળ", "tf", aka = "tonne force,tf")

        // ======================= TORQUE (base: N·m) =======================
        val TQ = "torque"
        u("nm_t", TQ, 1.0, "newton-metre", "न्यूटन-मीटर", "", "N·m", aka = "n.m,nm torque", pop = 5)
        u("kgfcm", TQ, 0.0980665, "kgf·cm", "", "", "kgf·cm", pop = 3)
        u("lbfin", TQ, 0.1129848290276167, "lbf·in", "", "", "lbf·in")
        u("lbfft", TQ, 1.3558179483314004, "lbf·ft", "", "", "lbf·ft", aka = "ft-lb,lb-ft", pop = 4)
        u("kgfm", TQ, 9.80665, "kgf·m", "", "", "kgf·m", pop = 3)

        // ======================= FLOW (base: m³/s) =======================
        val FL = "flow"
        u("lpm", FL, 1.0 / 60000, "litre / minute", "लीटर/मिनट", "લિટર/મિનિટ", "L/min", aka = "lpm", pop = 5)
        u("gpm", FL, 6.30901964e-5, "gallon / minute (US)", "", "", "gpm", "US", "gpm")
        u("lps", FL, 1e-3, "litre / second", "लीटर/सेकंड", "", "L/s", aka = "lps", pop = 3)
        u("m3h", FL, 1.0 / 3600, "m³ / hour", "घन मीटर/घंटा", "", "m³/h", aka = "m3/h,cmh", pop = 4)
        u("cfm", FL, 4.719474432e-4, "cubic foot / minute", "", "", "cfm", pop = 3)
        u("mld", FL, 1000.0 / 86400, "MLD (million litre / day)", "MLD", "MLD", "MLD", "IN", "mld")
        u("cusec", FL, 0.028316846592, "cusec (cft / second)", "क्यूसेक", "ક્યુસેક", "cusec", "IN", "cusec", "nahar / dam", pop = 3)
        u("cumec", FL, 1.0, "cumec (m³ / second)", "क्यूमेक", "", "m³/s", aka = "cumec,m3/s")

        // ======================= DENSITY (base: kg/m³) =======================
        val DE = "dens"
        u("gl", DE, 1.0, "g / L", "", "", "g/L")
        u("kgm3", DE, 1.0, "kg / m³", "", "", "kg/m³", aka = "kg/m3", pop = 4)
        u("lbft3", DE, 16.01846337, "lb / ft³", "", "", "lb/ft³", pop = 3)
        u("kgl", DE, 1000.0, "kg / L (= g/cm³)", "", "", "kg/L", aka = "g/cc,g/cm3,kg/l", pop = 4)
        u("lbin3", DE, 27679.9047, "lb / in³", "", "", "lb/in³")

        // ======================= FUEL (base: km/L) =======================
        val FU = "fuel"
        u("kmpl", FU, 1.0, "km / litre", "किमी/लीटर", "કિમી/લિટર", "km/L", aka = "kmpl,km/l", pop = 6)
        u("l100", FU, 100.0, "litre / 100 km", "लीटर/100 किमी", "લિટર/100 કિમી", "L/100km", aka = "l/100km", inv = true, pop = 4)
        u("mpg_us", FU, 0.425143707, "mpg (US)", "", "", "mpg", "US", "mpg", pop = 3)
        u("mpg_uk", FU, 0.35400619, "mpg (UK)", "", "", "mpg", "GB")

        // ======================= ACCELERATION (base: m/s²) =======================
        u("ms2", "acc", 1.0, "m / s²", "", "", "m/s²", aka = "m/s2")
        u("gal_acc", "acc", 0.01, "gal (cm/s²)", "", "", "Gal")
        u("fts2", "acc", 0.3048, "ft / s²", "", "", "ft/s²")
        u("gforce", "acc", 9.80665, "g-force", "जी-फ़ोर्स", "", "g", aka = "g force,gforce", pop = 4)

        // ======================= FREQUENCY (base: Hz) =======================
        u("hz", "freq", 1.0, "hertz", "हर्ट्ज़", "હર્ટ્ઝ", "Hz", aka = "hz,hertz", pop = 5)
        u("rpm", "freq", 1.0 / 60, "RPM (chakkar / minute)", "RPM", "RPM", "rpm", aka = "rpm", pop = 5)
        u("rads", "freq", 1.0 / (2 * PI), "rad / second", "", "", "rad/s")
        u("khz", "freq", 1e3, "kilohertz", "", "", "kHz", aka = "khz")
        u("mhz", "freq", 1e6, "megahertz", "", "", "MHz", aka = "mhz")
        u("ghz", "freq", 1e9, "gigahertz", "", "", "GHz", aka = "ghz")

        // ======================= ELECTRIC =======================
        for ((cat, sym, nm) in listOf(Triple("volt", "V", "volt"), Triple("curr", "A", "ampere"), Triple("res", "Ω", "ohm"),
            Triple("cap", "F", "farad"), Triple("ind", "H", "henry"))) {
            val pre = listOf(Triple("p", 1e-12, "pico"), Triple("n", 1e-9, "nano"), Triple("µ", 1e-6, "micro"), Triple("m", 1e-3, "milli"),
                Triple("", 1.0, ""), Triple("k", 1e3, "kilo"), Triple("M", 1e6, "mega"))
            for ((p, f, pn) in pre) {
                if (cat == "volt" && (p == "p" || p == "n")) continue
                if (cat == "curr" && (p == "p" || p == "M")) continue
                if (cat == "res" && (p == "p" || p == "n" || p == "µ")) continue
                if ((cat == "cap" || cat == "ind") && (p == "k" || p == "M")) continue
                val s = p + sym
                u(cat + "_" + p.ifEmpty { "1" }, cat, f, pn + nm, "", "", s, aka = s.lowercase() + "," + pn + nm + (if (p == "µ") ",u$sym".lowercase() else ""), pop = if (p.isEmpty()) 4 else 2)
            }
        }
        u("mah", "charge", 3.6, "mAh (battery)", "mAh (बैटरी)", "mAh (બેટરી)", "mAh", aka = "mah", pop = 5)
        u("ah", "charge", 3600.0, "Ah (amp-hour)", "Ah", "Ah", "Ah", aka = "ah,amp hour", pop = 5)
        u("coul", "charge", 1.0, "coulomb", "कूलॉम", "કુલંબ", "C", aka = "coulomb", pop = 3)

        // ======================= LIGHT / RADIATION / MAGNETIC =======================
        u("lux", "light", 1.0, "lux", "लक्स", "લક્સ", "lx", aka = "lux,lx", pop = 5)
        u("fc", "light", 10.7639104, "foot-candle", "", "", "fc", aka = "foot candle")
        u("phot", "light", 1e4, "phot", "", "", "ph")
        u("bq", "rad", 1.0, "becquerel", "बेकरेल", "", "Bq", aka = "becquerel", pop = 4)
        u("kbq", "rad", 1e3, "kilobecquerel", "", "", "kBq")
        u("mbq", "rad", 1e6, "megabecquerel", "", "", "MBq")
        u("ci", "rad", 3.7e10, "curie", "क्यूरी", "", "Ci", aka = "curie", pop = 3)
        u("gy", "dose", 1.0, "gray (Gy)", "ग्रे", "", "Gy", aka = "gray")
        u("rad_d", "dose", 0.01, "rad", "", "", "rad")
        u("sv", "dose", 1.0, "sievert (Sv)", "सीवर्ट", "", "Sv", aka = "sievert", pop = 4)
        u("msv", "dose", 1e-3, "millisievert", "", "", "mSv", aka = "msv", pop = 4)
        u("rem", "dose", 0.01, "rem", "", "", "rem")
        u("tesla", "mag", 1.0, "tesla", "टेस्ला", "ટેસ્લા", "T", aka = "tesla", pop = 4)
        u("mt", "mag", 1e-3, "millitesla", "", "", "mT")
        u("ut", "mag", 1e-6, "microtesla", "", "", "µT", aka = "microtesla")
        u("gauss", "mag", 1e-4, "gauss", "गॉस", "ગૉસ", "G", aka = "gauss", pop = 4)

        // ======================= CONCENTRATION / VISCOSITY / AMOUNT =======================
        u("pct", "conc", 0.01, "percent", "प्रतिशत", "ટકા", "%", aka = "percent,%,taka,pratishat", pop = 5)
        u("permil", "conc", 1e-3, "per mille", "", "", "‰")
        u("ppm", "conc", 1e-6, "ppm", "ppm", "ppm", "ppm", aka = "ppm", pop = 5)
        u("ppb", "conc", 1e-9, "ppb", "", "", "ppb", aka = "ppb")
        u("ppt", "conc", 1e-12, "ppt", "", "", "ppt")
        u("pas", "visc", 1.0, "pascal-second", "", "", "Pa·s", pop = 3)
        u("poise", "visc", 0.1, "poise", "पॉइज़", "", "P", aka = "poise")
        u("cp", "visc", 1e-3, "centipoise", "", "", "cP", aka = "centipoise,cp", pop = 4)
        u("mol", "amount", 1.0, "mole", "मोल", "મોલ", "mol", aka = "mole", pop = 4)
        u("mmol", "amount", 1e-3, "millimole", "", "", "mmol")
        u("kmol", "amount", 1e3, "kilomole", "", "", "kmol")
    }

    // ---------------- countries (for the flag filter) ----------------
    /** code → name; "XX" = ancient world, "" = used everywhere */
    val COUNTRIES = listOf(
        "IN" to "India", "CN" to "China", "HK" to "Hong Kong", "JP" to "Japan", "KR" to "Korea", "RU" to "Russia",
        "DE" to "Germany", "FR" to "France", "GB" to "Britain", "US" to "USA", "ES" to "Spain", "PT" to "Portugal",
        "BR" to "Brazil", "SE" to "Sweden / Norway", "TR" to "Turkey", "AE" to "Arab", "EG" to "Egypt", "IR" to "Iran",
        "NP" to "Nepal", "BD" to "Bangladesh", "LK" to "Sri Lanka", "TH" to "Thailand", "MY" to "Malaysia",
        "MM" to "Myanmar", "XX" to "Prachin (ancient)"
    )

    fun flag(cc: String): String = when (cc) {
        "" -> "🌐"
        "XX" -> "🏛️"
        else -> cc.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    }

    fun cat(id: String) = CATS.first { it.id == id }
    fun byId(id: String) = UNITS.firstOrNull { it.id == id }
    fun inCat(cat: String) = UNITS.filter { it.cat == cat }

    // ---------------- finding a unit by any name ----------------
    fun norm(s: String) = s.lowercase().replace("²", "2").replace("³", "3").replace("µ", "u")
        .replace(Regex("[\\s.]+"), " ").trim()

    /** every alias → units that use it (default / most used first) */
    private val ALL: Map<String, List<UDef>> by lazy {
        val m = LinkedHashMap<String, ArrayList<UDef>>()
        fun put(k: String, d: UDef) { val n = norm(k); if (n.isEmpty()) return; val l = m.getOrPut(n) { ArrayList() }; if (d !in l) l.add(d) }
        val byPop = UNITS.sortedByDescending { it.pop }
        for (d in byPop) put(d.sym, d)
        for (d in byPop) { put(d.en, d); put(d.hi, d); put(d.gu, d); d.aka.forEach { put(it, d) } }
        val strip = Regex("\\s*\\(.*\\)\\s*")
        for (d in byPop) { put(d.en.replace(strip, ""), d); put(d.hi.replace(strip, ""), d); put(d.gu.replace(strip, ""), d) }
        for (d in UNITS) put(d.id, d)
        for ((k, l) in m.toList()) if (k.length > 3 && !m.containsKey(k + "s")) m[k + "s"] = l
        m
    }
    private val ALIAS_SORTED: List<String> by lazy { ALL.keys.sortedByDescending { it.length } }

    fun find(name: String): UDef? = ALL[norm(name)]?.firstOrNull()
    private fun cands(name: String): List<UDef> = ALL[norm(name)] ?: emptyList()

    /** units whose names contain the text (for the search list) */
    fun search(q: String, cat: String? = null): List<UDef> {
        val n = norm(q)
        if (n.isEmpty()) return emptyList()
        return UNITS.filter { (cat == null || it.cat == cat) &&
            (norm(it.en).contains(n) || norm(it.hi).contains(n) || norm(it.gu).contains(n) || norm(it.sym) == n || it.aka.any { a -> norm(a).startsWith(n) }) }
            .sortedByDescending { it.pop }
    }

    // ---------------- converting ----------------
    fun convert(v: Double, from: UDef, to: UDef): Double = to.fromBase(from.toBase(v))

    /** result of a typed line like "2 vigha acre me" or "5 ft + 30 cm" */
    class Result(val base: Double, val cat: String, val from: UDef, val to: UDef?, val terms: Int)

    private val SEP = Regex("(?<= )(in|to|into|me|mein|ma|maa|માં|में|kitna|kitne|kitni|ketla|ketli|ketlu|કેટલા|કેટલું|कितना|कितने|=|→|->)(?= )|\\s*(=|→|->)\\s*")
    private val TAILWORD = Regex("\\s+(me|mein|ma|maa|માં|में|kitna|kitne|kitni|ketla|ketli|ketlu|કેટલા|કેટલું|कितना|कितने|kya|che|chhe|hai|हैं|है|છે)$")
    private val NUM = Regex("^[-+]?\\s*(\\d[\\d,]*\\.?\\d*|\\.\\d+)(\\s*[eE][-+]?\\d+)?(\\s*/\\s*\\d+(\\.\\d+)?)?")

    /** read a unit name at the start of [s]; returns the alias and the characters used */
    private fun unitAt(s: String): String? {
        for (a in ALIAS_SORTED) {
            if (s.startsWith(a)) {
                val next = s.getOrNull(a.length)
                if (next == null || !next.isLetterOrDigit()) return a
            }
        }
        return null
    }

    private fun num(t: String): Double {
        val parts = t.replace(",", "").replace(" ", "").split('/')
        val a = parts[0].toDoubleOrNull() ?: return Double.NaN
        return if (parts.size == 2) a / (parts[1].toDoubleOrNull() ?: return Double.NaN) else a
    }

    private class Term(val v: Double, val alias: String?)

    /** "2 vigha acre me", "5 ft + 30 cm", "5ft 3in to cm", "100 °C in F", "1 lakh" … null if not understood */
    fun parse(text: String): Result? {
        var s = norm(text.replace("×", "*")).replace("?", " ").trim()
        while (true) { val t = s.replace(TAILWORD, "").trim(); if (t == s) break; s = t }
        if (s.isEmpty()) return null
        // 1) the whole line is an amount: "5 ft + 30 cm", "2 bigha"
        build(s, null)?.let { return it }
        // 2) "… in / to / me / = <unit>"
        val p = " $s "
        for (m in SEP.findAll(p).toList().reversed()) {
            val tail = p.substring(m.range.last + 1).trim()
            var head = p.substring(0, m.range.first).trim()
            while (true) { val t = head.replace(TAILWORD, "").trim(); if (t == head) break; head = t }
            if (tail.isEmpty() || head.isEmpty() || tail.any { it.isDigit() }) continue
            build(head, tail)?.let { return it }
        }
        // 3) "2 vigha acre" (two units side by side)
        for (i in s.length - 1 downTo 1) {
            if (s[i - 1] != ' ') continue
            val tail = s.substring(i)
            if (tail.any { it.isDigit() } || cands(tail).isEmpty()) continue
            build(s.substring(0, i).trim(), tail)?.let { return it }
        }
        return null
    }

    /** amount text + optional target name → result, choosing among same-named units so the categories match */
    private fun build(head: String, tail: String?): Result? {
        val terms = parseTerms(head) ?: return null
        val toC: List<UDef?> = if (tail == null) listOf(null) else cands(tail).ifEmpty { return null }
        val aliases = terms.mapNotNull { it.alias }.distinct()
        val opts: Map<String, List<UDef>> = aliases.associateWith { cands(it) }
        val cats = LinkedHashSet<String>()
        (opts[terms.first().alias ?: ""] ?: listOf(byId("one")!!)).forEach { cats.add(it.cat) }
        toC.filterNotNull().forEach { cats.add(it.cat) }
        for (to in toC) {
            for (cat in cats) {
                if (to != null && to.cat != cat) continue
                val pick = HashMap<String, UDef>()
                var ok = true
                for (a in aliases) { val d = opts[a]!!.firstOrNull { it.cat == cat }; if (d == null) { ok = false; break }; pick[a] = d }
                if (!ok) continue
                val noUnit = terms.all { it.alias == null }
                if (noUnit && cat != "count") continue
                val units = terms.map { if (it.alias == null) byId("one")!! else pick.getValue(it.alias) }
                if (units.any { it.cat != cat }) continue
                if (cat == "temp" && terms.size > 1) continue
                val base = terms.indices.sumOf { units[it].toBase(terms[it].v) }
                return Result(base, cat, units.first(), to, terms.size)
            }
        }
        return null
    }

    /** "5 ft + 30 cm - 2 in" / "5 ft 3 in" → terms; a number without unit takes the last unit;
     *  a unit without a number is allowed only at the very start ("acre" = 1 acre) */
    private fun parseTerms(s0: String): List<Term>? {
        var s = s0.trim()
        val out = ArrayList<Term>()
        var sign = 1.0
        var last: String? = null
        var guard = 0
        while (s.isNotEmpty() && guard++ < 40) {
            s = s.trimStart()
            if (s.startsWith("+")) { sign = 1.0; s = s.substring(1); continue }
            if (s.startsWith("-") && out.isNotEmpty()) { sign = -1.0; s = s.substring(1); continue }
            var v = 1.0
            val m = NUM.find(s)
            var hadNum = false
            if (m != null && m.value.isNotBlank()) {
                v = num(m.value); hadNum = true
                if (!v.isFinite()) return null
                s = s.substring(m.value.length).trimStart()
                while (s.startsWith("*") || s.startsWith("x ")) {
                    s = s.substring(1).trimStart()
                    val m2 = NUM.find(s) ?: return null
                    v *= num(m2.value); s = s.substring(m2.value.length).trimStart()
                }
            }
            if (!hadNum && out.isNotEmpty()) return null
            val a = unitAt(s)
            val alias: String?
            if (a != null) { alias = a; s = s.substring(a.length) }
            else if (hadNum && last != null) alias = last
            else if (hadNum && s.isEmpty() && out.isEmpty()) alias = null
            else return null
            out.add(Term(sign * v, alias))
            last = alias
            sign = 1.0
        }
        return if (out.isEmpty()) null else out
    }

    // ---------------- showing numbers ----------------
    private val SUP = mapOf('-' to '⁻', '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹')

    /** 6 significant digits; Indian grouping (12,34,567); very small / big as 1.6 × 10⁻³⁵ */
    fun fmt(x: Double, indian: Boolean = true): String {
        if (!x.isFinite()) return "—"
        if (x == 0.0) return "0"
        val a = abs(x)
        if (a < 1e-4 || a >= 1e15) {
            var e = floor(log10(a)).toInt()
            var m = x / 10.0.pow(e)
            if (abs(m) >= 9.999995) { m /= 10; e++ }
            val ms = trimZeros(String.format(java.util.Locale.US, "%.4f", m))
            return ms + " × 10" + e.toString().map { SUP[it] ?: it }.joinToString("")
        }
        val digits = (5 - floor(log10(a)).toInt()).coerceIn(0, 9)
        val r = String.format(java.util.Locale.US, "%." + digits + "f", x)
        val t = trimZeros(r)
        val neg = t.startsWith("-")
        val body = t.removePrefix("-")
        val ip = body.substringBefore('.')
        val fp = if (body.contains('.')) "." + body.substringAfter('.') else ""
        return (if (neg) "-" else "") + group(ip, indian) + fp
    }

    private fun trimZeros(s: String) = if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s

    private fun group(ip: String, indian: Boolean): String {
        if (ip.length <= 3) return ip
        if (!indian) return ip.reversed().chunked(3).joinToString(",").reversed()
        val last3 = ip.takeLast(3)
        val rest = ip.dropLast(3)
        return rest.reversed().chunked(2).joinToString(",").reversed() + "," + last3
    }

    /** "≈ 4 basketball court" for length / area / weight / volume */
    fun feel(cat: String, base: Double, lang: Int): String? {
        val refs = REFS.filter { it.cat == cat }
        if (refs.isEmpty() || !(base > 0)) return null
        val best = refs.minByOrNull { r -> val q = base / r.v; if (q < 0.5) 99.0 + 1 / q else abs(log10(q) - 0.6) } ?: return null
        val q = base / best.v
        if (q < 0.3 || q > 5000) return null
        val n = when { q >= 100 -> fmt(q.roundToLong().toDouble()); q >= 10 -> q.roundToLong().toString(); else -> trimZeros(String.format(java.util.Locale.US, "%.1f", q)) }
        val name = when (lang) { 1 -> best.hi; 2 -> best.gu; else -> best.en }
        return "≈ $n × $name"
    }
}
