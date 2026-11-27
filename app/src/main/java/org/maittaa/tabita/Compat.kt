package org.maittaa.tabita

import android.os.Build

/**
 * Пояснения (5.19.1), почему функция может не работать на этом телефоне.
 * Показываются только когда ограничение действительно срабатывает.
 */
object Compat {
    val sdk get() = Build.VERSION.SDK_INT
    /** Цвета обоев (Material You) — с Android 12. */
    val dynamicColors get() = sdk >= 31
    /** Тематические (одноцветные) значки — с Android 13. */
    val themedIcons get() = sdk >= 33
    /** Health Connect встроен в систему — с Android 14. */
    val hcBuiltIn get() = sdk >= 34

    fun hcHelp(): String = if (hcBuiltIn)
        t("На Android 14 и новее Health Connect встроен в систему: проверьте «Настройки → Безопасность и конфиденциальность → Health Connect» и обновите систему.")
    else
        t("На Android 9–13 Health Connect — отдельное приложение: установите или обновите его в Google Play. Без него данные часов и веса можно вносить вручную.")

    fun dynamicHelp() = t("Цвета обоев (Material You) есть только с Android 12 — здесь доступны акцентные цвета на выбор.")
    fun themedIconHelp() = t("Тематические значки под цвет обоев появились в Android 13 — здесь значок будет цветным.")
}
