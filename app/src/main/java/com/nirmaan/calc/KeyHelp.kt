package com.nirmaan.calc

/** Long-press help for every key (main label and Conv label). */
object KeyHelp {
    private val H = mapOf(
        "Cut Speed" to "Cutting speed (SFM ya Metric mein m/min). Value + Cut Speed = save ($). Sirf Cut Speed = RPM + Diam se nikalta hai; dobara dabane par DIA, RPM.",
        "RPM" to "Spindle RPM. Value + RPM = save. Sirf RPM = Cut Speed + Diam se (jaise .375 Diam, 300 Cut Speed, RPM = 3056). Dobara: CUT, DIA.",
        "Feed Rate" to "Feed rate in/min (mm/min). Sirf Feed Rate = RPM × Cut Feed, ya RPM × #Teeth × Feed/Tooth. Dobara: RPM, Feed/Tooth, Teeth.",
        "Bolt Pattern" to "Bolt circle: Adj = center X, Opp = center Y, Angle = pehla hole, Diam = circle. Holes ki ginti + Bolt Pattern, phir dabate jaayein: OC-OC, X-01, Y-01, X-02 ...",
        "Thread Size" to "Size + Thread Size (8, 1/4 Inch, 5 mm), phir TPI/pitch + Thread Size (jaise 32 ya 0.75). Ya size ke baad Thread Size dabate jaayein = standard pitch, = se chunein. Phir dabate jaayein: Tap drill, Roll-tap drill, Close, Free, pitch/minor/major dia.",
        "#Teeth" to "Cutter ke daant (flutes) ki ginti.",
        "Feed/Tooth" to "Chip load: har daant ki feed. Bina value: Feed Rate ÷ (RPM × #Teeth).",
        "Cut Feed" to "Feed per revolution (IPR / mm per rev). Bina value: Feed Rate ÷ RPM.",
        "Diam" to "Diameter save. Dobara dabane par AREA, CIRC. Conv + Diam = Radius.",
        "Drill Size" to "36 = #36 drill, .25 Inch, 6.8 mm, ya Conv + 8 (Alpha) letter drill. Dobara dabane par agli badi drill.",
        "Adj (x)" to "Right triangle: Adjacent (x). Koi 2 value daaliye (x, y, r, Ø), phir teesri bina value dabayein.",
        "Opp (y)" to "Right triangle: Opposite (y).",
        "Hyp (r)" to "Right triangle: Hypotenuse (r).",
        "Angle (Ø)" to "Right triangle angle. Dobara dabane par doosra angle (90° − Ø).",
        "Inch" to "Inch.", "Clear" to "Display saaf (memory nahi).",
        "1/1000\"" to "Thou: number = 1/1000 inch. Length par dabayein = thou mein.",
        "RCT" to "Radial chip thinning: Feed/Tooth + Diam save karke, stepover + Conv Cut Speed = factor; dobara = adjusted Feed/Tooth, Feed Rate.",
        "3-W Measure" to "3-wire: dabate jaayein = 3W MIN, 3W MAX, P-DIA MIN, MAX (wire size na ho to ideal wire). Naap + 3-W Measure = pitch dia.",
        "Wire Size" to "Thread ke liye wire: IDEAL, MAX, MIN. Wire size + Conv Feed Rate = save.",
        "Thread Class" to "Class dikhata hai (INT 2B). Dobara dabane par INT ⇄ EXT (metric: 6H → 6g → 6G → 6h → 6f → 6e). 1/2/3 + Thread Class = class; metric 3–9 = grade.",
        "%Thread" to "Tap drill ke liye % (default 75). 65 + Conv Thread Size = 65%. Drill size daal kar = kitna % thread.",
        "Drill Point" to "Drill ki tip ki lambai (118°). Angle + Conv Drill Size = naya angle.",
        "Sine" to "SIN.", "Cosine" to "COS.", "Tangent" to "TAN.", "ArcSine" to "ASIN.", "ArcCos" to "ACOS.", "ArcTan" to "ATAN.",
        "grams" to "Gram.", "metric tons" to "Metric ton.", "tons" to "Ton.", "Alpha" to "Agle phase mein.", "Rcl" to "Recall memory.",
        "Clear All" to "Saari memory saaf.",
        "Rise" to "Rise (oonchai) store karein: 12 Feet [Rise]. Bina value dabane par baaki values se Rise nikalta hai. Circle + Arc ho to arc ki segment height.",
        "R/Wall" to "Rake Wall: dhalan wali deewar ke studs ki height. Pehle length daal kar dabayein to woh Base wall ban jaati hai.",
        "Run" to "Run (aadha span / aadi doori) store karein. Bina value dabane par nikalta hai. Circle + Arc ho to chord length.",
        "Roof" to "Roof: chhat ka area, squares, sheets, rafter aur ridge. Pehle area (jaise 1300 Feet Feet) daal sakte hain.",
        "Pitch" to "Pitch store karein: 30 = degree, 8 Inches = 8/12, 20 % = grade. Bina value: Rise/Run se pitch. Dobara dabane par Rafter screen.",
        "Slope" to "Slope: number ko rise ÷ run maan kar pitch banata hai (jaise 0.5 = 26.57°).",
        "Diag" to "Diagonal (rafter length). Rise + Run ke baad [Diag]. Dobara dabane par poori Common Rafter screen (ridge deduction, birdsmouth, overhang).",
        "Polygon" to "Polygon: pehle Diameter [Circle], phir sides ka number (jaise 6) aur Conv + Diag.",
        "Stair" to "Stair: seedhi ka poora hisaab aur drawing. Rise pehle daal kar dabayein.",
        "Baluster" to "Baluster: railing ki patti/jaali ki ginti aur spacing (Limit Opening, Evenly Space, Best Fit).",
        "Hip/V" to "Hip / Valley rafter: plumb, level, cheek, backing, dihedral, sheathing angle. Miter Saw ya Protractor.",
        "IrPitch" to "Irregular Hip/Valley: jab dono taraf ki chhat ka pitch alag ho.",
        "Jack" to "Jack rafters: har jack ki lambai, common difference, cheek cut. Chhote se ya bade se shuru.",
        "IrJack" to "Irregular Jack rafters: alag pitch wali chhat; On-Center ya Mating spacing.",
        "Arc" to "Arc: angle ya length store. Run + Rise ke baad [Arc] = angle, dobara = poori list aur arch ke studs.",
        "Radius" to "Radius store / nikalna. Dobara dabane par Circle list.",
        "Circle" to "Diameter store / nikalna. Dobara dabane par circumference, area.",
        "ColCon" to "Column / Cone: Height aur Diameter se volume aur area.",
        "CmpMtr" to "Compound miter: crown molding ke miter aur bevel angle. Pehle corner angle ya corners ginti daal sakte hain.",
        "Fence" to "Fence: posts, rails, pickets. Panels ki ginti daal kar dabayein to length batata hai.",
        "m" to "Metre (Conv + Yards). m dobara dabane par m → cm → mm.",
        "Length" to "Length store karein (kamra, deewar, footing, drywall ke liye).",
        "Masonry" to "Masonry: int, block, tile, paver ki ginti, cement aur ret. Area pehle daal sakte hain.",
        "Width" to "Width store. Length + Width ke baad dobara dabane par area, perimeter, square-up.",
        "Footing" to "Footing / concrete: volume, cement bag, ret, kapchi (M15/M20/M25).",
        "Height" to "Height store. L + W + H ke baad dobara: volume, deewar area, kamre ka area.",
        "Drywall" to "Drywall / sheets / paint: kamra, deewar, area ya sirf length se. Apni sheet size bhi daal sakte hain.",
        "SIN" to "Sine. Conv ke saath ASIN.", "COS" to "Cosine. Conv ke saath ACOS.", "TAN" to "Tangent. Conv ke saath ATAN.",
        "⌫" to "Aakhri digit mitaayein.", "√x" to "Square root (area ka root = length).",
        "Yards" to "Yard. Dobara = yd², phir yd³. Wazan par dabane se volume (wt/vol se).",
        "Feet" to "Feet. 9 Feet 10 Inches. Dobara = ft², ft³. Decimal/fraction badalne ke liye dobara dabayein.",
        "Inches" to "Inches. 7 / 8 Inches jaise fraction. Dobara dabane par decimal.",
        "/" to "Fraction: 3 / 8 = 3/8.", "Frac" to "Fraction resolution badlein: 1/2 → 1/4 → ... → 1/64.",
        "%" to "Percent. 200 + 10 % = 220.", "x²" to "Square.",
        "Conv" to "Conv button ke 2 hisse (45° par): upar-baayein CONV = keys ke upar likha peela kaam. Neeche-daayein SWITCH = doosra calculator (Nirmaan ⇄ Machinist).",
        "7" to "7", "cm" to "Centimetre (Conv + Feet). Dobara = cm², cm³.", "8" to "8",
        "Litre" to "Litre: number daal kar Litre dabayein. Koi bhi volume (m³ / cft / yd³) par dabayein to litre mein. 1000 litre = 1 m³. Wapas: Feet ya Conv + Yards (m).",
        "x10ⁿ" to "x10ⁿ: bade/chhote number. 2.5 [Conv x10ⁿ] 3 = 2500.",
        "Trig" to "Trig: green buttons par ek vaar SIN / COS / TAN, pachhi wapas Length / Width / Height.",
        "Metric" to "Metric: Yards/Feet/Inches ⇄ m/cm/mm button badlein (Prefs mein bhi).",
        "BdFt" to "Board feet: number ya volume ko BF mein. Bina value: lakdi ka cft / rate hisaab.",
        "9" to "9", "mm" to "Millimetre (Conv + Inches).", "÷" to "Bhaag.", "1/x" to "1 ÷ x.",
        "Store" to "Store: phir M1/M2/M3 (1,2,3), o.c. (5), Rails (7), wt/vol (0), ya Rise/Run/... dabayein.",
        "Prefs" to "Settings: theme, units, fraction, ton, saved results, update.",
        "4" to "4", "lbs" to "Pounds (wazan). Volume par dabane se wazan (wt/vol se).",
        "5" to "5", "qty@oc" to "Kitne studs/khambe: length aur on-center spacing se. Ya members se length.",
        "6" to "6", "Tons" to "Tons (2000 ya 2240 lb, Prefs mein).", "×" to "Guna.", "ClrAll" to "Saari memory saaf.",
        "Recall" to "Recall: phir M1/M2/M3, o.c., wt/vol, Rise/Run... dabayein. Recall Recall = M+ saaf.",
        "M-R/C" to "M+ memory saaf.", "1" to "1", "kg" to "Kilogram. Volume par dabane se wazan.",
        "2" to "2", "Acre" to "Acre mein badlein.", "3" to "3", "met tons" to "Metric ton (1000 kg).",
        "−" to "Minus.", "+/-" to "Plus/minus badlein.",
        "M+" to "M+ memory mein jodein. Recall M+ = total, phir average, phir count.", "M-" to "M+ memory se ghataein.",
        "0" to "0", "Cost" to "Kharcha: 9 Yards Yards Yards × 5000 Conv Cost = ₹ total. Bina value: rate aur GST screen.",
        "." to "Point. 23.16.45 = 23° 16' 45\".", "dms⇄deg" to "DMS ↔ degree. Dobara dabate raho: pitch, % pitch, % slope, radians.",
        "=" to "Barabar.", "Tape" to "Pichle hisaab (history).", "+" to "Jodein.", "π" to "Pi (3.14159)."
    )

    fun text(main: String, conv: String, blue: String): String {
        val sb = StringBuilder()
        sb.append("[").append(main).append("]  ").append(H[main] ?: "")
        if (conv.isNotEmpty()) sb.append("\n\n[Conv + ").append(main).append("] = ").append(conv).append(":  ").append(H[conv] ?: "")
        if (blue.isNotEmpty()) sb.append("\n\n[Store/Recall + ").append(main).append("] = ").append(blue).append(
            when (blue) {
                "wt/vol" -> ":  density (wazan per volume). Store 0 = save (dobara = unit badlein), Recall 0 = dekhein. Bina value Store 0 = volume se wazan screen."
                "o.c." -> ":  on-center spacing save (studs, jacks, rake wall, arch)."
                "Rails" -> ":  fence ke har section mein rails."
                else -> ":  permanent memory."
            }
        )
        return sb.toString()
    }
}
