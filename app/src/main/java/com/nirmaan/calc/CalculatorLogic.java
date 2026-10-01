package com.nirmaan.calc;

import java.util.HashMap;
import java.util.List;

/** Micro logic: hands the typed line to the engine (SciMath) and keeps Ans and X Y Z. */
public final class CalculatorLogic {
    public final HashMap<String, SQ> vars = new HashMap<>();
    public SQ ans;
    public UDef ansUnit;

    public static final class Result {
        public boolean error;
        public String num = "", unit = "", info = "", raw = "";
        public String assign;      // "X" when solved / stored
        public Boolean truth;      // "2+2=4"
    }

    private static String t(SciOpts o, String en, String hi, String gu) {
        return o.getLang() == 1 ? hi : o.getLang() == 2 ? gu : en;
    }

    public Result eval(String expr, SciOpts o) {
        Result r = new Result();
        try {
            SciMath.Out out = SciMath.INSTANCE.run(expr, new SciMath.Env(vars, o.getAngle(), ans), o.getComma());
            if (out.getTruth() != null) { r.truth = out.getTruth(); return r; }
            ans = out.getQ();
            ansUnit = out.getUnit();
            r.assign = out.getAssign();
            if (r.assign != null) vars.put(r.assign, out.getQ());
            SciFmt.Shown sh = SciFmt.INSTANCE.show(out.getQ(), out.getUnit(), o);
            r.num = sh.getNum();
            r.unit = sh.getUnit();
            StringBuilder info = new StringBuilder();
            if (sh.getApprox() != null) info.append(sh.getApprox());
            List<SQ> roots = out.getRoots();
            if (roots.size() > 1) {
                if (info.length() > 0) info.append("   ");
                info.append(r.assign).append(": ");
                for (int i = 0; i < roots.size(); i++) {
                    SciFmt.Shown x = SciFmt.INSTANCE.show(roots.get(i), out.getUnit(), o);
                    if (i > 0) info.append(",  ");
                    info.append((x.getNum() + " " + x.getUnit()).trim());
                }
            }
            r.info = info.toString();
            r.raw = r.assign != null ? r.assign : SciFmt.INSTANCE.rawOf(out.getQ(), out.getUnit(), o);
        } catch (Exception e) {
            // SciErr is a Kotlin (unchecked) exception, so it is caught here
            r.error = true;
            r.info = e instanceof SciErr ? errText((SciErr) e, o) : "";
        }
        return r;
    }

    /** the same answer again in new settings (DEG/RAD, decimals, EXACT …) */
    public SciFmt.Shown showAns(SciOpts o) {
        return ans == null ? null : SciFmt.INSTANCE.show(ans, ansUnit, o);
    }

    public static String errText(SciErr e, SciOpts o) {
        String a = e.getArg();
        switch (e.getCode()) {
            case "syntax": return t(o, "Something is wrong near: " + a, "यहाँ गलत लिखा है: " + a, "અહીં ખોટું લખ્યું છે: " + a);
            case "paren": return t(o, "Brackets ( ) do not match", "ब्रैकेट ( ) ठीक नहीं", "કૌંસ ( ) બરાબર નથી");
            case "unknown": return t(o, "Not understood: " + a, "समझ नहीं आया: " + a, "સમજાયું નહીં: " + a);
            case "mismatch": return t(o, "Units do not match: " + a, "यूनिट मेल नहीं खाते: " + a, "યુનિટ મેળ ખાતા નથી: " + a);
            case "needplain": return t(o, "No unit allowed in " + a + "( )", a + "( ) के अंदर यूनिट नहीं चलेगा", a + "( ) અંદર યુનિટ નહીં ચાલે");
            case "domain": return t(o, "Cannot be worked out: " + a, "यह हिसाब नहीं हो सकता: " + a, "આ હિસાબ થઈ શકે નહીં: " + a);
            case "args": return t(o, "Write: ∫(f, a, b)  d/dx(f, a)  Σ(f, a, b)", "ऐसे लिखो: ∫(f, a, b)  d/dx(f, a)  Σ(f, a, b)", "આમ લખો: ∫(f, a, b)  d/dx(f, a)  Σ(f, a, b)");
            case "noroot": return t(o, "No answer found for " + a, a + " का जवाब नहीं मिला", a + " નો જવાબ મળ્યો નહીં");
            case "nounit": return t(o, "Pick a unit after _", "_ के बाद यूनिट चुनो", "_ પછી યુનિટ પસંદ કરો");
            case "incomplete": return t(o, "Incomplete", "अधूरा है", "અધૂરું છે");
            case "big": return t(o, "Range too big", "रेंज बहुत बड़ी है", "રેન્જ બહુ મોટી છે");
            default: return e.getCode();
        }
    }
}
