package com.devxrohan.smusic.constants

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.devxrohan.smusic.R

enum class AppFont(val title: String, val key: String) {
    SYSTEM("System Default", "system"),
    LINOTTE("Linotte", "linotte"),
    POPPINS("Poppins", "poppins"),
    SF_PRO("SF Pro Display", "sf_pro"),
    ANYBODY("Anybody", "anybody"),
    GREAT_VIBES("Great Vibes", "great_vibes"),
    SANS_SERIF("Sans-Serif", "sans_serif"),
    SERIF("Serif", "serif"),
    MONOSPACE("Monospace", "monospace"),
    CURSIVE("Cursive", "cursive"),
    GOTHAM("Gotham", "gotham"),
    TORMENT_PULSATION("Torment Pulsation", "torment_pulsation"),
    BEST_SOULMATE("Best Soulmate", "best_soulmate"),
    BADDIE("Baddie", "baddie");

    fun getFontFamily(): FontFamily {
        return when (this) {
            SYSTEM -> FontFamily.Default
            LINOTTE -> FontFamily(Font(R.font.linotte))
            POPPINS -> FontFamily(
                Font(R.font.poppins_regular, FontWeight.Normal),
                Font(R.font.poppins_medium, FontWeight.Medium),
                Font(R.font.poppins_bold, FontWeight.Bold)
            )
            SF_PRO -> FontFamily(Font(R.font.sfprodisplaybold, FontWeight.Bold))
            ANYBODY -> FontFamily(Font(R.font.anybody))
            GREAT_VIBES -> FontFamily(Font(R.font.great_vibes))
            SANS_SERIF -> FontFamily.SansSerif
            SERIF -> FontFamily.Serif
            MONOSPACE -> FontFamily.Monospace
            CURSIVE -> FontFamily.Cursive
            GOTHAM -> FontFamily(
                Font(R.font.gotham_light, FontWeight.Light),
                Font(R.font.gotham_regular, FontWeight.Normal),
                Font(R.font.gotham_regular, FontWeight.Medium),
                Font(R.font.gotham_medium, FontWeight.SemiBold),
                Font(R.font.gotham_bold, FontWeight.Bold),
                Font(R.font.gotham_bold, FontWeight.ExtraBold),
                Font(R.font.gotham_bold, FontWeight.Black),
                Font(R.font.gotham_italic, FontWeight.Normal, androidx.compose.ui.text.font.FontStyle.Italic)
            )
            TORMENT_PULSATION -> FontFamily(Font(R.font.torment_pulsation))
            BEST_SOULMATE -> FontFamily(Font(R.font.best_soulmate))
            BADDIE -> FontFamily(Font(R.font.pacifico_regular))
        }
    }

    companion object {
        fun fromKey(key: String?): AppFont {
            return entries.find { it.key == key } ?: LINOTTE
        }
    }
}
