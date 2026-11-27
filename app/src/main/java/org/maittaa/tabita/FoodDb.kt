package org.maittaa.tabita

/**
 * Справочник продуктов (5.21). На 100 г съедобной части (напитки — на 100 мл).
 * Калории и БЖУ — USDA FoodData Central (SR Legacy / Foundation) и «Химический состав российских пищевых продуктов»
 * (Скурихин, Тутельян) для типично российских продуктов; ГИ — по шкале глюкозы (глюкоза = 100),
 * International Tables of Glycemic Index (Atkinson, Foster-Powell, Brand-Miller, 2008; обновление 2021), средние значения.
 * gi = null — ГИ не измерялся или углеводов почти нет (ГН ≈ 0). Готовые блюда — примерные, рецепты разные.
 */
data class Food(
    val ru: String, val en: String, val cat: String,
    val kcal: Double, val p: Double, val f: Double, val c: Double,
    val gi: Int? = null,
    /** Порция по умолчанию, г (1 шт., ложка, стакан); null — 100 г. */
    val portion: Int? = null, val portionRu: String? = null, val portionEn: String? = null,
    /** Напиток — «на 100 мл». */
    val ml: Boolean = false,
    /** Примерное значение (готовое блюдо). */
    val approx: Boolean = false,
    /** Свой продукт (5.22.2): id, единица («мерная ложка») и её вес, порция по этикетке. */
    val id: String? = null,
    val unit: String? = null, val unitG: Double? = null,
    val labelG: Double? = null,
    /** 5.27: на 100 г — клетчатка, сахара, насыщенные жиры (если известны: свои продукты с этикетки). */
    val fib: Double? = null, val sug: Double? = null, val sat: Double? = null,
    /** ГИ взят «как у похожего продукта» — показывается с «≈». */
    val giEst: Boolean = false,
) {
    val name get() = if (I18n.isEn) en else ru
    val portionName get() = if (I18n.isEn) portionEn else portionRu
    val mine get() = id != null
    /** Ключ для избранного и счётчика использования. */
    val key get() = id ?: ru

    fun toJson(): org.json.JSONObject = org.json.JSONObject().put("id", id).put("ru", ru).put("kcal", kcal).put("p", p).put("f", f).put("c", c)
        .put("gi", gi).put("unit", unit).put("unitG", unitG).put("labelG", labelG).put("ml", ml).put("approx", approx)
        .put("fib", fib).put("sug", sug).put("sat", sat).put("giEst", giEst)

    companion object {
        fun fromJson(o: org.json.JSONObject): Food {
            fun d(k: String) = o.optDouble(k).takeIf { !it.isNaN() }
            val name = o.getString("ru")
            return Food(name, name, "my", d("kcal") ?: 0.0, d("p") ?: 0.0, d("f") ?: 0.0, d("c") ?: 0.0,
                gi = if (o.has("gi") && !o.isNull("gi")) o.optInt("gi") else null, ml = o.optBoolean("ml"), approx = o.optBoolean("approx"),
                id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                unit = o.optString("unit").ifBlank { null }, unitG = d("unitG"), labelG = d("labelG"),
                fib = d("fib"), sug = d("sug"), sat = d("sat"), giEst = o.optBoolean("giEst"))
        }

        /** Свой продукт из цифр этикетки «на порцию» [labelG] г. */
        fun fromLabel(name: String, labelG: Double, kcal: Double, p: Double, f: Double, c: Double, gi: Int?,
                      unit: String?, unitsPerLabel: Double?, id: String = java.util.UUID.randomUUID().toString(),
                      ml: Boolean = false, approx: Boolean = false,
                      fib: Double? = null, sug: Double? = null, sat: Double? = null, giEst: Boolean = false): Food {
            val k = 100 / labelG
            fun r(v: Double) = Math.round(v * k * 100) / 100.0
            return Food(name, name, "my", r(kcal), r(p), r(f), r(c), gi = gi, id = id, unit = unit?.ifBlank { null }, ml = ml, approx = approx,
                unitG = unitsPerLabel?.takeIf { it > 0 && !unit.isNullOrBlank() }?.let { Math.round(labelG / it * 100) / 100.0 }, labelG = labelG,
                fib = fib?.let { r(it) }, sug = sug?.let { r(it) }, sat = sat?.let { r(it) }, giEst = giEst && gi != null)
        }

        /** Калории по БЖУ (Этуотер: 4 / 9 / 4). */
        fun kcalOf(p: Double, f: Double, c: Double) = 4 * p + 9 * f + 4 * c
    }
}

object FoodDb {
    /** Категории: ключ → (ru, en). */
    val cats = listOf(
        "veg" to ("Овощи" to "Vegetables"),
        "fruit" to ("Фрукты, ягоды" to "Fruit"),
        "grain" to ("Крупы, макароны" to "Grains, pasta"),
        "bread" to ("Хлеб, хлопья" to "Bread, cereal"),
        "legume" to ("Бобовые, соя" to "Legumes, soy"),
        "dairy" to ("Молочное" to "Dairy"),
        "meat" to ("Мясо, птица" to "Meat, poultry"),
        "egg" to ("Яйца" to "Eggs"),
        "fish" to ("Рыба, морепродукты" to "Fish, seafood"),
        "nuts" to ("Орехи, семена" to "Nuts, seeds"),
        "fat" to ("Масла, соусы" to "Oils, sauces"),
        "sweet" to ("Сладкое, снеки" to "Sweets, snacks"),
        "drink" to ("Напитки" to "Drinks"),
        "dish" to ("Готовые блюда" to "Dishes"),
    )

    fun catName(k: String) = cats.firstOrNull { it.first == k }?.second?.let { if (I18n.isEn) it.second else it.first } ?: k

    private fun F(ru: String, en: String, cat: String, kcal: Number, p: Number, f: Number, c: Number, gi: Int? = null,
                  portion: Int? = null, pr: String? = null, pe: String? = null, ml: Boolean = false, approx: Boolean = false) =
        Food(ru, en, cat, kcal.toDouble(), p.toDouble(), f.toDouble(), c.toDouble(), gi, portion, pr, pe, ml, approx)

    val all: List<Food> = listOf(
        // ——— овощи (сырые, если не сказано иное) ———
        F("Капуста белокочанная", "White cabbage", "veg", 25, 1.3, 0.1, 5.8),
        F("Капуста квашеная", "Sauerkraut", "veg", 19, 0.9, 0.1, 4.3),
        F("Капуста цветная", "Cauliflower", "veg", 25, 1.9, 0.3, 5.0),
        F("Брокколи", "Broccoli", "veg", 34, 2.8, 0.4, 6.6),
        F("Брокколи отварная", "Broccoli, boiled", "veg", 35, 2.4, 0.4, 7.2),
        F("Помидоры", "Tomatoes", "veg", 18, 0.9, 0.2, 3.9),
        F("Огурцы", "Cucumbers", "veg", 15, 0.7, 0.1, 3.6),
        F("Перец сладкий красный", "Red bell pepper", "veg", 31, 1.0, 0.3, 6.0),
        F("Морковь", "Carrot", "veg", 41, 0.9, 0.2, 9.6, 16),
        F("Морковь отварная", "Carrot, boiled", "veg", 35, 0.8, 0.2, 8.2, 39),
        F("Лук репчатый", "Onion", "veg", 40, 1.1, 0.1, 9.3),
        F("Чеснок", "Garlic", "veg", 149, 6.4, 0.5, 33.1),
        F("Свёкла отварная", "Beetroot, boiled", "veg", 44, 1.7, 0.2, 10.0, 64),
        F("Кабачок", "Zucchini", "veg", 17, 1.2, 0.3, 3.1),
        F("Баклажан", "Eggplant", "veg", 25, 1.0, 0.2, 5.9),
        F("Тыква отварная", "Pumpkin, boiled", "veg", 20, 0.7, 0.1, 4.9, 64),
        F("Шпинат", "Spinach", "veg", 23, 2.9, 0.4, 3.6),
        F("Салат листовой", "Lettuce", "veg", 15, 1.4, 0.2, 2.9),
        F("Редис", "Radish", "veg", 16, 0.7, 0.1, 3.4),
        F("Шампиньоны", "Mushrooms (button)", "veg", 22, 3.1, 0.3, 3.3),
        F("Картофель отварной", "Potato, boiled", "veg", 87, 1.9, 0.1, 20.1, 78),
        F("Картофель запечённый", "Potato, baked", "veg", 93, 2.5, 0.1, 21.2, 85),
        F("Картофельное пюре (молоко, масло)", "Mashed potato (milk, butter)", "veg", 113, 1.9, 4.2, 16.8, 87),
        F("Картофель фри", "French fries", "veg", 312, 3.4, 14.7, 41.4, 63),
        F("Батат запечённый", "Sweet potato, baked", "veg", 90, 2.0, 0.2, 20.7, 63),
        F("Кукуруза сладкая отварная", "Sweet corn, boiled", "veg", 96, 3.4, 1.5, 21.0, 52),
        F("Горошек зелёный отварной", "Green peas, boiled", "veg", 84, 5.4, 0.2, 15.6, 51),
        F("Авокадо", "Avocado", "veg", 160, 2.0, 14.7, 8.5),
        F("Маслины, оливки", "Olives", "veg", 115, 0.8, 10.7, 6.3),

        // ——— фрукты и ягоды ———
        F("Яблоко", "Apple", "fruit", 52, 0.3, 0.2, 13.8, 36, 180, "1 шт. ≈ 180 г", "1 pc ≈ 180 g"),
        F("Груша", "Pear", "fruit", 57, 0.4, 0.1, 15.2, 38, 180, "1 шт. ≈ 180 г", "1 pc ≈ 180 g"),
        F("Банан", "Banana", "fruit", 89, 1.1, 0.3, 22.8, 51, 120, "1 шт. без кожуры ≈ 120 г", "1 peeled ≈ 120 g"),
        F("Апельсин", "Orange", "fruit", 47, 0.9, 0.1, 11.8, 43, 150, "1 шт. ≈ 150 г", "1 pc ≈ 150 g"),
        F("Мандарин", "Mandarin", "fruit", 53, 0.8, 0.3, 13.3, 47, 75, "1 шт. ≈ 75 г", "1 pc ≈ 75 g"),
        F("Грейпфрут", "Grapefruit", "fruit", 42, 0.8, 0.1, 10.7, 25),
        F("Персик", "Peach", "fruit", 39, 0.9, 0.3, 9.5, 42),
        F("Абрикос", "Apricot", "fruit", 48, 1.4, 0.4, 11.1, 34),
        F("Слива", "Plum", "fruit", 46, 0.7, 0.3, 11.4, 39),
        F("Вишня", "Sour cherries", "fruit", 50, 1.0, 0.3, 12.2, 22),
        F("Черешня", "Sweet cherries", "fruit", 63, 1.1, 0.2, 16.0, 63),
        F("Виноград", "Grapes", "fruit", 69, 0.7, 0.2, 18.1, 59),
        F("Киви", "Kiwi", "fruit", 61, 1.1, 0.5, 14.7, 50),
        F("Манго", "Mango", "fruit", 60, 0.8, 0.4, 15.0, 51),
        F("Ананас", "Pineapple", "fruit", 50, 0.5, 0.1, 13.1, 59),
        F("Арбуз", "Watermelon", "fruit", 30, 0.6, 0.2, 7.6, 76),
        F("Дыня", "Melon (cantaloupe)", "fruit", 34, 0.8, 0.2, 8.2, 65),
        F("Хурма", "Persimmon", "fruit", 70, 0.6, 0.2, 18.6),
        F("Гранат", "Pomegranate", "fruit", 83, 1.7, 1.2, 18.7),
        F("Клубника", "Strawberries", "fruit", 32, 0.7, 0.3, 7.7, 40),
        F("Малина", "Raspberries", "fruit", 52, 1.2, 0.7, 11.9),
        F("Черника, голубика", "Blueberries", "fruit", 57, 0.7, 0.3, 14.5, 53),
        F("Смородина чёрная", "Blackcurrant", "fruit", 63, 1.4, 0.4, 15.4),
        F("Финики", "Dates", "fruit", 282, 2.5, 0.4, 75.0, 42),
        F("Изюм", "Raisins", "fruit", 299, 3.1, 0.5, 79.2, 64),
        F("Курага", "Dried apricots", "fruit", 241, 3.4, 0.5, 62.6, 31),
        F("Чернослив", "Prunes", "fruit", 240, 2.2, 0.4, 63.9, 29),

        // ——— крупы и макароны (отварные — сварены в воде без масла) ———
        F("Гречка отварная", "Buckwheat, cooked", "grain", 92, 3.4, 0.6, 19.9, 50),
        F("Гречка (сухая крупа)", "Buckwheat, dry", "grain", 343, 13.3, 3.4, 71.5, 50),
        F("Рис белый отварной", "White rice, cooked", "grain", 130, 2.7, 0.3, 28.2, 73),
        F("Рис белый (сухой)", "White rice, dry", "grain", 365, 7.1, 0.7, 80.0, 73),
        F("Рис бурый отварной", "Brown rice, cooked", "grain", 123, 2.7, 1.0, 25.6, 68),
        F("Рис басмати отварной", "Basmati rice, cooked", "grain", 121, 3.5, 0.4, 25.2, 58),
        F("Овсянка на воде", "Oatmeal, water", "grain", 71, 2.5, 1.5, 12.0, 55),
        F("Овсяные хлопья (сухие)", "Rolled oats, dry", "grain", 379, 13.2, 6.5, 67.7, 55),
        F("Овсянка быстрого приготовления", "Instant oatmeal", "grain", 68, 2.4, 1.4, 11.7, 79),
        F("Перловка отварная", "Pearl barley, cooked", "grain", 123, 2.3, 0.4, 28.2, 28),
        F("Пшено отварное", "Millet, cooked", "grain", 119, 3.5, 1.0, 23.7, 71),
        F("Булгур отварной", "Bulgur, cooked", "grain", 83, 3.1, 0.2, 18.6, 48),
        F("Кускус отварной", "Couscous, cooked", "grain", 112, 3.8, 0.2, 23.2, 65),
        F("Киноа отварная", "Quinoa, cooked", "grain", 120, 4.4, 1.9, 21.3, 53),
        F("Макароны отварные", "Pasta, cooked", "grain", 158, 5.8, 0.9, 30.9, 49),
        F("Макароны (сухие)", "Pasta, dry", "grain", 371, 13.0, 1.5, 74.7, 49),
        F("Макароны цельнозерновые отварные", "Whole-wheat pasta, cooked", "grain", 149, 6.0, 1.7, 30.1, 48),

        // ——— хлеб и хлопья ———
        F("Хлеб пшеничный белый", "White bread", "bread", 265, 9.0, 3.2, 49.0, 75, 30, "1 ломтик ≈ 30 г", "1 slice ≈ 30 g"),
        F("Батон нарезной", "White loaf (baton)", "bread", 262, 7.5, 2.9, 51.4, 75, 30, "1 ломтик ≈ 30 г", "1 slice ≈ 30 g"),
        F("Хлеб цельнозерновой", "Whole-wheat bread", "bread", 252, 12.5, 3.5, 42.7, 74, 30, "1 ломтик ≈ 30 г", "1 slice ≈ 30 g"),
        F("Хлеб «Бородинский»", "Borodinsky rye bread", "bread", 208, 6.8, 1.3, 40.7, 58, 30, "1 ломтик ≈ 30 г", "1 slice ≈ 30 g"),
        F("Лаваш тонкий", "Lavash (thin)", "bread", 236, 7.9, 1.0, 47.6),
        F("Хлебцы хрустящие ржаные", "Rye crispbread", "bread", 334, 9.4, 1.2, 70.0, 64, 10, "1 шт. ≈ 10 г", "1 pc ≈ 10 g"),
        F("Хлебцы рисовые", "Rice cakes", "bread", 387, 8.2, 2.8, 81.5, 82, 9, "1 шт. ≈ 9 г", "1 pc ≈ 9 g"),
        F("Кукурузные хлопья", "Corn flakes", "bread", 357, 7.5, 0.4, 84.0, 81),
        F("Мюсли без сахара", "Muesli, no sugar", "bread", 362, 9.7, 5.9, 66.0, 57),

        // ——— бобовые и соя (отварные) ———
        F("Фасоль красная отварная", "Kidney beans, boiled", "legume", 127, 8.7, 0.5, 22.8, 24),
        F("Чечевица отварная", "Lentils, boiled", "legume", 116, 9.0, 0.4, 20.1, 32),
        F("Нут отварной", "Chickpeas, boiled", "legume", 164, 8.9, 2.6, 27.4, 28),
        F("Горох колотый отварной", "Split peas, boiled", "legume", 118, 8.3, 0.4, 21.1, 25),
        F("Соя отварная", "Soybeans, boiled", "legume", 172, 18.2, 9.0, 8.4, 16),
        F("Тофу", "Tofu", "legume", 76, 8.1, 4.8, 1.9),
        F("Соевое молоко без сахара", "Soy milk, unsweetened", "legume", 33, 2.9, 1.6, 1.7, ml = true),

        // ——— молочное ———
        F("Молоко 1%", "Milk 1%", "dairy", 42, 3.0, 1.0, 4.9, 37, 250, "стакан 250 мл", "glass 250 ml", ml = true),
        F("Молоко 2,5%", "Milk 2.5%", "dairy", 52, 2.8, 2.5, 4.7, 39, 250, "стакан 250 мл", "glass 250 ml", ml = true),
        F("Молоко 3,2%", "Milk 3.2%", "dairy", 59, 2.9, 3.2, 4.7, 39, 250, "стакан 250 мл", "glass 250 ml", ml = true),
        F("Кефир 1%", "Kefir 1%", "dairy", 40, 3.0, 1.0, 4.0, null, 250, "стакан 250 мл", "glass 250 ml", ml = true),
        F("Кефир 2,5%", "Kefir 2.5%", "dairy", 50, 2.9, 2.5, 4.0, null, 250, "стакан 250 мл", "glass 250 ml", ml = true),
        F("Йогурт натуральный 2%", "Plain yogurt 2%", "dairy", 60, 4.3, 2.0, 6.2, 41),
        F("Йогурт греческий 2%", "Greek yogurt 2%", "dairy", 73, 9.9, 1.9, 3.9),
        F("Йогурт фруктовый", "Fruit yogurt", "dairy", 99, 4.0, 1.5, 17.0, 41),
        F("Творог 0–1%", "Cottage cheese 0–1%", "dairy", 77, 16.0, 0.6, 1.8),
        F("Творог 5%", "Cottage cheese 5%", "dairy", 121, 17.2, 5.0, 1.8),
        F("Творог 9%", "Cottage cheese 9%", "dairy", 159, 16.7, 9.0, 2.0),
        F("Сметана 15%", "Sour cream 15%", "dairy", 158, 2.6, 15.0, 3.0),
        F("Сметана 20%", "Sour cream 20%", "dairy", 206, 2.8, 20.0, 3.2),
        F("Сливки 10%", "Cream 10%", "dairy", 118, 3.0, 10.0, 4.0, ml = true),
        F("Сыр твёрдый («Российский»)", "Hard cheese", "dairy", 360, 24.0, 29.0, 0.3),
        F("Моцарелла", "Mozzarella", "dairy", 300, 22.2, 22.4, 2.2),
        F("Брынза, фета", "Feta, brynza", "dairy", 260, 17.9, 20.1, 0.5),
        F("Масло сливочное 82,5%", "Butter 82.5%", "dairy", 748, 0.5, 82.5, 0.8, null, 10, "1 ч. л. ≈ 10 г", "1 tsp ≈ 10 g"),
        F("Мороженое пломбир", "Ice cream (plombir)", "dairy", 227, 3.2, 15.0, 20.8, 51),
        F("Сгущённое молоко с сахаром", "Sweetened condensed milk", "dairy", 320, 7.2, 8.5, 56.0, 61),

        // ——— мясо и птица (готовые) ———
        F("Куриная грудка (варёная/запечённая)", "Chicken breast, cooked", "meat", 165, 31.0, 3.6, 0.0),
        F("Куриное бедро без кожи, запечённое", "Chicken thigh, skinless, roasted", "meat", 209, 26.0, 10.9, 0.0),
        F("Курица с кожей, жареная", "Chicken with skin, fried", "meat", 245, 26.8, 14.6, 0.0),
        F("Индейка, грудка запечённая", "Turkey breast, roasted", "meat", 147, 30.1, 2.1, 0.0),
        F("Говядина нежирная тушёная/отварная", "Lean beef, stewed/boiled", "meat", 200, 30.0, 9.0, 0.0),
        F("Фарш говяжий 15%, жареный", "Ground beef 85%, cooked", "meat", 250, 25.9, 15.4, 0.0),
        F("Свинина, вырезка запечённая", "Pork tenderloin, roasted", "meat", 143, 26.2, 3.5, 0.0),
        F("Свинина, лопатка запечённая", "Pork shoulder, roasted", "meat", 248, 22.9, 16.6, 0.0),
        F("Баранина запечённая", "Lamb, roasted", "meat", 294, 24.5, 20.9, 0.0),
        F("Печень говяжья жареная", "Beef liver, pan-fried", "meat", 175, 26.5, 4.7, 5.2),
        F("Колбаса варёная «Докторская»", "Boiled sausage (Doktorskaya)", "meat", 257, 12.8, 22.2, 1.5),
        F("Сосиски молочные", "Frankfurters (milk)", "meat", 266, 11.0, 23.9, 1.6, null, 50, "1 шт. ≈ 50 г", "1 pc ≈ 50 g"),
        F("Бекон жареный", "Bacon, fried", "meat", 541, 37.0, 41.8, 1.4),

        // ——— яйца ———
        F("Яйцо куриное варёное", "Egg, boiled", "egg", 155, 12.6, 10.6, 1.1, null, 50, "1 шт. ≈ 50 г", "1 egg ≈ 50 g"),
        F("Яичный белок", "Egg white", "egg", 52, 10.9, 0.2, 0.7, null, 33, "белок 1 яйца ≈ 33 г", "1 egg white ≈ 33 g"),
        F("Яичный желток", "Egg yolk", "egg", 322, 15.9, 26.5, 3.6, null, 17, "желток 1 яйца ≈ 17 г", "1 yolk ≈ 17 g"),
        F("Омлет", "Omelet", "egg", 154, 10.6, 11.7, 0.6),
        F("Яичница", "Fried egg", "egg", 196, 13.6, 14.8, 0.8, null, 46, "1 яйцо ≈ 46 г", "1 egg ≈ 46 g"),

        // ——— рыба и морепродукты (готовые) ———
        F("Треска отварная", "Cod, cooked", "fish", 105, 22.8, 0.9, 0.0),
        F("Минтай отварной", "Pollock, cooked", "fish", 111, 23.5, 1.2, 0.0),
        F("Горбуша запечённая", "Pink salmon, baked", "fish", 153, 24.6, 5.3, 0.0),
        F("Сёмга (лосось) запечённая", "Atlantic salmon, baked", "fish", 206, 22.1, 12.4, 0.0),
        F("Скумбрия запечённая", "Mackerel, baked", "fish", 262, 23.9, 17.8, 0.0),
        F("Тунец консервированный в собственном соку", "Tuna, canned in water", "fish", 116, 25.5, 0.8, 0.0),
        F("Креветки варёные", "Shrimp, cooked", "fish", 99, 24.0, 0.3, 0.2),
        F("Крабовые палочки", "Imitation crab sticks", "fish", 95, 7.6, 0.5, 15.0),
        F("Печень трески консервированная", "Cod liver, canned", "fish", 613, 4.2, 65.7, 1.2),
        F("Шпроты в масле", "Sprats in oil", "fish", 363, 17.4, 32.4, 0.4),

        // ——— орехи и семена ———
        F("Миндаль", "Almonds", "nuts", 579, 21.2, 49.9, 21.6),
        F("Грецкий орех", "Walnuts", "nuts", 654, 15.2, 65.2, 13.7),
        F("Фундук", "Hazelnuts", "nuts", 628, 15.0, 60.8, 16.7),
        F("Кешью", "Cashews", "nuts", 553, 18.2, 43.9, 30.2, 25),
        F("Арахис", "Peanuts", "nuts", 567, 25.8, 49.2, 16.1, 14),
        F("Арахисовая паста", "Peanut butter", "nuts", 588, 25.1, 50.4, 20.0),
        F("Семечки подсолнечника", "Sunflower seeds", "nuts", 584, 20.8, 51.5, 20.0),
        F("Тыквенные семечки", "Pumpkin seeds", "nuts", 559, 30.2, 49.0, 10.7),
        F("Семена льна", "Flaxseed", "nuts", 534, 18.3, 42.2, 28.9),
        F("Семена чиа", "Chia seeds", "nuts", 486, 16.5, 30.7, 42.1),

        // ——— масла и соусы ———
        F("Масло подсолнечное", "Sunflower oil", "fat", 899, 0.0, 99.9, 0.0, null, 10, "1 ст. л. ≈ 10 г", "1 tbsp ≈ 10 g"),
        F("Масло оливковое", "Olive oil", "fat", 884, 0.0, 100.0, 0.0, null, 10, "1 ст. л. ≈ 10 г", "1 tbsp ≈ 10 g"),
        F("Майонез 67%", "Mayonnaise 67%", "fat", 620, 0.3, 67.0, 2.6, null, 15, "1 ст. л. ≈ 15 г", "1 tbsp ≈ 15 g"),
        F("Кетчуп", "Ketchup", "fat", 101, 1.0, 0.1, 27.4, null, 15, "1 ст. л. ≈ 15 г", "1 tbsp ≈ 15 g"),
        F("Соевый соус", "Soy sauce", "fat", 53, 8.1, 0.6, 4.9, null, 15, "1 ст. л. ≈ 15 г", "1 tbsp ≈ 15 g"),

        // ——— сладкое и снеки ———
        F("Сахар", "Sugar", "sweet", 387, 0.0, 0.0, 100.0, 65, 5, "1 ч. л. ≈ 5 г", "1 tsp ≈ 5 g"),
        F("Мёд", "Honey", "sweet", 304, 0.3, 0.0, 82.4, 61, 20, "1 ст. л. ≈ 20 г", "1 tbsp ≈ 20 g"),
        F("Варенье, джем", "Jam", "sweet", 278, 0.4, 0.1, 68.9, 51, 20, "1 ст. л. ≈ 20 г", "1 tbsp ≈ 20 g"),
        F("Шоколад горький 70–85%", "Dark chocolate 70–85%", "sweet", 598, 7.8, 42.6, 45.9, 23),
        F("Шоколад молочный", "Milk chocolate", "sweet", 535, 7.6, 29.7, 59.4, 43),
        F("Печенье овсяное", "Oatmeal cookies", "sweet", 437, 6.2, 17.6, 68.7, 55),
        F("Зефир", "Zefir (marshmallow)", "sweet", 326, 0.8, 0.1, 79.8),
        F("Халва подсолнечная", "Sunflower halva", "sweet", 516, 11.6, 29.7, 54.0),
        F("Попкорн без масла", "Popcorn, air-popped", "sweet", 387, 12.9, 4.5, 77.8, 65),
        F("Чипсы картофельные", "Potato chips", "sweet", 536, 7.0, 34.6, 53.0, 56),

        // ——— напитки (на 100 мл) ———
        F("Вода, чай, чёрный кофе без сахара", "Water, tea, black coffee", "drink", 1, 0.1, 0.0, 0.0, ml = true),
        F("Сок апельсиновый", "Orange juice", "drink", 45, 0.7, 0.2, 10.4, 50, 250, "стакан 250 мл", "glass 250 ml", ml = true),
        F("Сок яблочный", "Apple juice", "drink", 46, 0.1, 0.1, 11.3, 41, 250, "стакан 250 мл", "glass 250 ml", ml = true),
        F("Сок томатный", "Tomato juice", "drink", 17, 0.9, 0.3, 3.5, 38, 250, "стакан 250 мл", "glass 250 ml", ml = true),
        F("Кола", "Cola", "drink", 42, 0.0, 0.0, 10.6, 63, 330, "банка 330 мл", "can 330 ml", ml = true),
        F("Квас", "Kvass", "drink", 27, 0.2, 0.0, 5.2, null, 500, "0,5 л", "0.5 L", ml = true),
        F("Пиво светлое", "Beer, lager", "drink", 43, 0.5, 0.0, 3.6, null, 500, "0,5 л", "0.5 L", ml = true),
        F("Вино сухое", "Dry wine", "drink", 84, 0.1, 0.0, 2.6, null, 150, "бокал 150 мл", "glass 150 ml", ml = true),
        F("Водка", "Vodka", "drink", 231, 0.0, 0.0, 0.0, null, 50, "50 мл", "50 ml", ml = true),

        // ——— готовые блюда (примерно, рецепты разные) ———
        F("Борщ с мясом", "Borscht with meat", "dish", 50, 2.5, 2.5, 5.0, null, 300, "тарелка 300 г", "bowl 300 g", approx = true),
        F("Суп куриный с лапшой", "Chicken noodle soup", "dish", 50, 3.0, 1.5, 6.0, null, 300, "тарелка 300 г", "bowl 300 g", approx = true),
        F("Плов с курицей", "Pilaf with chicken", "dish", 180, 8.0, 7.0, 21.0, null, 250, "порция 250 г", "serving 250 g", approx = true),
        F("Пельмени отварные", "Pelmeni, boiled", "dish", 256, 12.0, 12.0, 25.0, null, 200, "порция 200 г", "serving 200 g", approx = true),
        F("Салат «Оливье» с майонезом", "Olivier salad (mayo)", "dish", 198, 5.5, 16.5, 7.0, null, 150, "порция 150 г", "serving 150 g", approx = true),
        F("Салат из свежих овощей с маслом", "Fresh vegetable salad with oil", "dish", 70, 1.0, 5.5, 4.0, null, 150, "порция 150 г", "serving 150 g", approx = true),
        F("Пицца с сыром", "Cheese pizza", "dish", 266, 11.4, 9.7, 33.3, 80, 110, "кусок ≈ 110 г", "slice ≈ 110 g", approx = true),
        F("Гамбургер", "Hamburger", "dish", 254, 12.9, 11.8, 24.9, 66, 110, "1 шт. ≈ 110 г", "1 pc ≈ 110 g", approx = true),
        F("Сырники", "Syrniki", "dish", 220, 15.0, 11.0, 15.0, null, 50, "1 шт. ≈ 50 г", "1 pc ≈ 50 g", approx = true),
    )

    /** Поиск: все слова запроса — в названии (рус. и англ.), регистр и «ё» не важны. */
    /** Свои продукты — подставляются из Repo. */
    @Volatile var mine: List<Food> = emptyList()

    /** Начальные свои продукты — по этикеткам, присланным пользователем (порция на банке). */
    val seed: List<Food> get() = listOf(
        // «Формула 1», сухая смесь: порция 26 г = 3 мерные ложки — 90 ккал, Б 10, Ж 1,5, У 9 (сахар 9), клетчатка 3
        Food.fromLabel("Формула 1 (Herbalife), сухая смесь", 26.0, 90.0, 10.0, 1.5, 9.0, null, "мерная ложка", 3.0, id = "seed_f1", fib = 3.0, sug = 9.0),
        // зелёная смесь: порция 17 г = 2 мерные ложки — 50 ккал, Б 3, Ж 0,4, У 6,3, клетчатка 5,5
        Food.fromLabel("Грин Макс (Herbalife), зелёная смесь", 17.0, 50.0, 3.0, 0.4, 6.3, null, "мерная ложка", 2.0, id = "seed_green", fib = 5.5),
        // H24 «Восстановление силы» (Rebuild Strength), шоколад: 50 г = 5 мерных ложек — 190 ккал, Б 25, Ж 1,5, У 18
        Food.fromLabel("H24 Восстановление силы (Herbalife)", 50.0, 190.0, 25.0, 1.5, 18.0, null, "мерная ложка", 5.0, id = "seed_rebuild"),
        // CR7 Drive: суточная порция 27 г = 2 мерные ложки по 13,5 г — 95 ккал, У 23, белка и жира нет
        Food.fromLabel("CR7 Drive (Herbalife)", 27.0, 95.0, 0.0, 0.0, 23.0, null, "мерная ложка", 2.0, id = "seed_cr7"),
        // протеиновый батончик H24 Achieve, Cookie dough: 60 г — 200 ккал, Б 21, Ж 6, У 24
        Food.fromLabel("Батончик H24 Achieve (Herbalife)", 60.0, 200.0, 21.0, 6.0, 24.0, null, "батончик", 1.0, id = "seed_bar"),
        // алоэ-концентрат: порция 15 мл = 3 колпачка. На российской этикетке цифр не видно — взяты с этикетки Herbalife
        // для ЮАР (15 мл: 94 кДж ≈ 22 ккал, У 6 г, сахар 5 г): примерно, уточните по своей бутылке
        Food.fromLabel("Алоэ-концентрат (Herbalife), клюква", 15.0, 22.0, 0.0, 0.0, 6.0, null, "колпачок", 3.0, id = "seed_aloe", ml = true, approx = true, sug = 5.0),
        // «Комплекс пищевых волокон»: 2 мерные ложки = 6 г, 3 г растворимых волокон; энергии на этикетке нет —
        // по американской этикетке (7 г — 15 ккал, почти всё — клетчатка) ≈ 13 ккал на 6 г, усваиваемых углеводов ~1 г
        Food.fromLabel("Комплекс пищевых волокон (Herbalife)", 6.0, 13.0, 0.0, 0.0, 1.0, null, "мерная ложка", 2.0, id = "seed_fiber", approx = true, fib = 3.0),
    )

    /** Версия набора начальных продуктов: при обновлении недостающие добавляются один раз (удалённые не возвращаются). */
    const val SEED_VER = 2

    /** Описание своих продуктов для подсказки ИИ. */
    fun mineForAi(): String = mine.joinToString("; ") { f ->
        val u = if (f.ml) "ml" else "g"
        "\"${f.ru}\": per 100 $u ${f.kcal} kcal, protein ${f.p} g, fat ${f.f} g, carbs ${f.c} g" +
            (f.labelG?.let { ", label serving $it $u" } ?: "") +
            (if (f.unit != null && f.unitG != null) ", 1 ${f.unit} = ${f.unitG} $u" else "")
    }

    /** Продукты справочника с измеренным ГИ, похожие по названию на [name] — для «≈ ГИ как у похожего». */
    fun similarGi(name: String, limit: Int = 6): List<Food> {
        fun norm(s: String) = s.lowercase().replace('ё', 'е')
        val words = norm(name).split(' ', ',', '(', ')', '-').filter { it.length >= 3 }.map { it.take(maxOf(3, it.length - 2)) }
        if (words.isEmpty()) return emptyList()
        return all.filter { it.gi != null }.map { f -> f to words.count { w -> norm(f.ru).contains(w) || norm(f.en).contains(w) } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    fun search(q: String, cat: String?): List<Food> {
        fun norm(s: String) = s.lowercase().replace('ё', 'е')
        val words = norm(q).split(' ', ',').filter { it.isNotBlank() }
        return (mine + all).filter { f ->
            (cat == null || f.cat == cat) && words.all { w -> norm(f.ru).contains(w) || norm(f.en).contains(w) }
        }
    }

    /** Добавить [grams] продукта к приёму пищи: суммирует калории и БЖУ, ГИ — средневзвешенный по углеводам, ГН — сумма. */
    fun addTo(meal: Meal, f: Food, grams: Double): Meal {
        val k = grams / 100
        val c = f.c * k
        val oldC = meal.carbs ?: 0.0
        val gi = when {
            f.gi == null || c < 0.5 -> meal.gi
            meal.gi == null || oldC < 0.5 -> f.gi.toDouble()
            else -> (meal.gi * oldC + f.gi * c) / (oldC + c)
        }
        val gl = if (f.gi != null) (meal.glEff ?: 0.0) + f.gi * c / 100 else meal.gl
        fun r1(v: Double) = Math.round(v * 10) / 10.0
        val units = f.unitG?.let { grams / it }?.takeIf { Math.abs(it * 2 - Math.round(it * 2)) < 0.02 }
        val g = formatNumber(Math.round(grams).toDouble()) + if (f.ml) " " + t("мл") else " " + t("г")
        val line = f.name + " " + if (units != null && f.unit != null) formatNumber(Math.round(units * 2) / 2.0) + " × " + f.unit + " (" + g + ")" else g
        return meal.copy(
            text = listOf(meal.text.trim(), line).filter { it.isNotEmpty() }.joinToString(", "),
            kcal = Math.round((meal.kcal ?: 0.0) + f.kcal * k).toDouble(),
            protein = r1((meal.protein ?: 0.0) + f.p * k),
            fat = r1((meal.fat ?: 0.0) + f.f * k),
            carbs = r1(oldC + c),
            gi = gi?.let { Math.round(it).toDouble() },
            gl = gl?.let { r1(it) },
        )
    }
}
