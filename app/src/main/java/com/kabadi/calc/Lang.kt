package com.kabadi.calc

/** UI text in English (0), Hindi (1), Gujarati (2). */
object L {
    var lang = 0

    private val T: Map<String, Array<String>> = mapOf(
        "app" to arrayOf("Kabadi Calculator", "कबाड़ी कैलकुलेटर", "કબાડી કેલ્ક્યુલેટર"),
        "new" to arrayOf("+ New Hisab", "+ नया हिसाब", "+ નવો હિસાબ"),
        "saved" to arrayOf("Saved hisab", "पुराने हिसाब", "જૂના હિસાબ"),
        "none" to arrayOf("No hisab yet. Tap + New Hisab.", "अभी कोई हिसाब नहीं। + नया हिसाब दबाएँ।", "હજી કોઈ હિસાબ નથી. + નવો હિસાબ દબાવો."),
        "settings" to arrayOf("Settings", "सेटिंग", "સેટિંગ"),
        "calc" to arrayOf("Calculator", "कैलकुलेटर", "કેલ્ક્યુલેટર"),
        "owner" to arrayOf("Owner / shop name", "मालिक / दुकान का नाम", "માલિક / દુકાનનું નામ"),
        "mobile" to arrayOf("Mobile number", "मोबाइल नंबर", "મોબાઇલ નંબર"),
        "address" to arrayOf("Address (optional)", "पता (ज़रूरी नहीं)", "સરનામું (જરૂરી નથી)"),
        "party" to arrayOf("Party name", "पार्टी का नाम", "પાર્ટીનું નામ"),
        "vehicle" to arrayOf("Vehicle no. / model", "गाड़ी नंबर / मॉडल", "ગાડી નંબર / મોડલ"),
        "note" to arrayOf("Note", "नोट", "નોંધ"),
        "date" to arrayOf("Date & time", "तारीख और समय", "તારીખ અને સમય"),
        "price" to arrayOf("1. Vehicle price", "1. गाड़ी की कीमत", "1. ગાડીની કિંમત"),
        "kharch" to arrayOf("2. Expenses (tap to add)", "2. खर्च (दबाकर जोड़ें)", "2. ખર્ચ (દબાવીને ઉમેરો)"),
        "maal" to arrayOf("3. Parts / maal (tap to add)", "3. माल (दबाकर जोड़ें)", "3. માલ (દબાવીને ઉમેરો)"),
        "other" to arrayOf("+ Other", "+ दूसरा", "+ બીજું"),
        "kg" to arrayOf("Kg", "किलो", "કિલો"),
        "rate" to arrayOf("Rate ₹/kg", "भाव ₹/किलो", "ભાવ ₹/કિલો"),
        "fix" to arrayOf("Fix", "फिक्स", "ફિક્સ"),
        "kgrate" to arrayOf("Kg × Rate", "किलो × भाव", "કિલો × ભાવ"),
        "amount" to arrayOf("Amount ₹", "रकम ₹", "રકમ ₹"),
        "total" to arrayOf("Total", "कुल", "કુલ"),
        "sum_price" to arrayOf("Vehicle price", "गाड़ी की कीमत", "ગાડીની કિંમત"),
        "sum_kharch" to arrayOf("Total expenses", "कुल खर्च", "કુલ ખર્ચ"),
        "sum_lagat" to arrayOf("Total cost (price + expenses)", "कुल लागत (कीमत + खर्च)", "કુલ લાગત (કિંમત + ખર્ચ)"),
        "sum_maal" to arrayOf("Parts total", "माल का कुल", "માલનો કુલ"),
        "sum_kg" to arrayOf("Total weight", "कुल वज़न", "કુલ વજન"),
        "profit" to arrayOf("PROFIT", "मुनाफा", "નફો"),
        "loss" to arrayOf("LOSS", "नुकसान", "નુકસાન"),
        "save" to arrayOf("Save", "सेव", "સેવ"),
        "jpg" to arrayOf("JPG", "JPG", "JPG"),
        "pdf" to arrayOf("PDF", "PDF", "PDF"),
        "share" to arrayOf("WhatsApp", "WhatsApp", "WhatsApp"),
        "delete" to arrayOf("Delete", "हटाएँ", "કાઢી નાખો"),
        "back" to arrayOf("Back", "वापस", "પાછા"),
        "saved_ok" to arrayOf("Saved", "सेव हो गया", "સેવ થઈ ગયું"),
        "del_q" to arrayOf("Delete this hisab?", "यह हिसाब हटाएँ?", "આ હિસાબ કાઢી નાખવો?"),
        "yes" to arrayOf("Yes", "हाँ", "હા"),
        "no" to arrayOf("No", "नहीं", "ના"),
        "lang" to arrayOf("Language", "भाषा", "ભાષા"),
        "name_q" to arrayOf("Name", "नाम", "નામ"),
        "hisab" to arrayOf("HISAB", "हिसाब", "હિસાબ"),
        "item" to arrayOf("Item", "आइटम", "વસ્તુ"),
        "img_ok" to arrayOf("Saved in Gallery (Pictures/KabadiCalc)", "गैलरी में सेव (Pictures/KabadiCalc)", "ગેલેરીમાં સેવ (Pictures/KabadiCalc)"),
        "pdf_ok" to arrayOf("Saved in Downloads/KabadiCalc", "Downloads/KabadiCalc में सेव", "Downloads/KabadiCalc માં સેવ"),
        "perm" to arrayOf("Allow storage permission and tap again", "स्टोरेज की अनुमति दें और फिर दबाएँ", "સ્ટોરેજની મંજૂરી આપો અને ફરી દબાવો"),
        "update" to arrayOf("Check for update", "अपडेट देखें", "અપડેટ તપાસો"),
        "lists" to arrayOf("My lists (add / remove buttons)", "मेरी लिस्ट (बटन जोड़ें / हटाएँ)", "મારી યાદી (બટન ઉમેરો / કાઢો)"),
        "parts_list" to arrayOf("Parts buttons", "माल के बटन", "માલના બટન"),
        "exp_list" to arrayOf("Expense buttons", "खर्च के बटन", "ખર્ચના બટન"),
        "add" to arrayOf("Add", "जोड़ें", "ઉમેરો"),
        "reset" to arrayOf("Default", "डिफ़ॉल्ट", "ડિફોલ્ટ"),
        "tip" to arrayOf(
            "Tip: in any box you can type 400+380 or 2*55.",
            "टिप: किसी भी खाने में 400+380 या 2*55 लिख सकते हैं।",
            "ટીપ: કોઈ પણ ખાનામાં 400+380 કે 2*55 લખી શકો છો."
        ),
        "search" to arrayOf("Search party / vehicle", "पार्टी / गाड़ी खोजें", "પાર્ટી / ગાડી શોધો"),
        "all_total" to arrayOf("All hisab profit", "सब हिसाब का मुनाफा", "બધા હિસાબનો નફો"),
        "copy" to arrayOf("Copy", "कॉपी", "કૉપિ"),
        "made" to arrayOf("Made with Kabadi Calculator", "कबाड़ी कैलकुलेटर से बना", "કબાડી કેલ્ક્યુલેટરથી બનાવ્યું")
    )

    fun t(k: String): String = T[k]?.get(lang) ?: k
}
