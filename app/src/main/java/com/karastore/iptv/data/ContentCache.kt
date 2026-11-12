package com.karastore.iptv.data

import com.karastore.iptv.data.model.Category
import com.karastore.iptv.data.model.ContentItem
import com.karastore.iptv.data.model.ContentType

/** تصنيف مع عدد عناصره */
data class CategoryCount(val id: String?, val name: String, val count: Int)

/**
 * كاش المحتوى داخل الجلسة: يحمّل كل عناصر النوع مرة واحدة،
 * ثم يشتق التصنيفات بالعدّاد و"المضاف مؤخراً" والتصفية محلياً.
 */
object ContentCache {

    private val items = mutableMapOf<ContentType, List<ContentItem>>()
    private val cats = mutableMapOf<ContentType, List<Category>>()
    // كاش لكل باقة على حدة (لتحميل أخف للكتالوجات الضخمة)
    private val catItems = mutableMapOf<String, List<ContentItem>>()
    private fun key(type: ContentType, categoryId: String?) = "${type.name}|${categoryId ?: "__all__"}"

    fun isLoaded(type: ContentType) = items.containsKey(type)

    /** يحمّل كل العناصر (مرة واحدة) ويحتفظ بها — ثقيل مع الكتالوجات الضخمة */
    suspend fun load(type: ContentType): List<ContentItem> {
        items[type]?.let { return it }
        val list = Repo.content(type, null)
        items[type] = list
        try { cats[type] = Repo.categories(type) } catch (_: Exception) {}
        return list
    }

    /** يحمّل قائمة التصنيفات فقط (خفيفة) */
    suspend fun ensureCategories(type: ContentType): List<Category> {
        cats[type]?.let { return it }
        val list = Repo.categories(type)
        cats[type] = list
        return list
    }

    fun categoriesList(type: ContentType): List<Category> = cats[type] ?: emptyList()

    /** يحمّل عناصر باقة واحدة فقط (مع كاش) */
    suspend fun loadCategory(type: ContentType, categoryId: String?): List<ContentItem> {
        val k = key(type, categoryId)
        catItems[k]?.let { return it }
        val list = Repo.content(type, categoryId)
        catItems[k] = list
        return list
    }

    /** كل العناصر المحمّلة فعلاً (للبحث ضمن المتصفّح) */
    fun loadedItems(type: ContentType): List<ContentItem> {
        val fromFull = items[type]
        if (fromFull != null) return fromFull
        return catItems.entries.filter { it.key.startsWith("${type.name}|") }.flatMap { it.value }
    }

    fun all(type: ContentType): List<ContentItem> = items[type] ?: emptyList()

    fun filter(type: ContentType, categoryId: String?, hidden: Set<String> = emptySet()): List<ContentItem> {
        // إن لم يُحمَّل الكتالوج الكامل، استعمل كاش الباقة أو المحمّل فعلاً
        val list = if (isLoaded(type)) all(type)
        else catItems[key(type, categoryId)] ?: loadedItems(type)
        return if (categoryId == null) list.filter { it.categoryId !in hidden }
        else list.filter { it.categoryId == categoryId }
    }

    /** التصنيفات مع عدد العناصر في كل تصنيف */
    fun categoriesWithCounts(type: ContentType): List<CategoryCount> {
        val catList = cats[type] ?: emptyList()
        // إن لم يُحمَّل الكتالوج الكامل: أعرض التصنيفات بدون عدّاد (تحميل خفيف)
        if (!isLoaded(type)) {
            val result = mutableListOf(CategoryCount(null, "__all__", 0))
            for (c in catList) result.add(CategoryCount(c.id, c.name, 0))
            return result
        }
        val list = all(type)
        val byCat = list.groupingBy { it.categoryId }.eachCount()
        val result = mutableListOf(CategoryCount(null, "__all__", list.size))
        for (c in catList) {
            val count = byCat[c.id] ?: 0
            if (count > 0) result.add(CategoryCount(c.id, c.name, count))
        }
        return result
    }

    /** أحدث العناصر المضافة (مع استثناء الباقات المخفية) */
    fun recentlyAdded(type: ContentType, n: Int, hidden: Set<String> = emptySet()): List<ContentItem> {
        return all(type).asSequence()
            .filter { it.categoryId !in hidden }
            .sortedByDescending { it.added ?: it.id.toLongOrNull() ?: 0L }
            .take(n).toList()
    }

    fun clearAll() {
        items.clear()
        cats.clear()
        catItems.clear()
    }
}
