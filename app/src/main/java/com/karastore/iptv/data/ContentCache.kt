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

    fun isLoaded(type: ContentType) = items.containsKey(type)

    /** يحمّل كل العناصر (مرة واحدة) ويحتفظ بها */
    suspend fun load(type: ContentType): List<ContentItem> {
        items[type]?.let { return it }
        val list = Repo.content(type, null)
        items[type] = list
        try { cats[type] = Repo.categories(type) } catch (_: Exception) {}
        return list
    }

    fun all(type: ContentType): List<ContentItem> = items[type] ?: emptyList()

    fun filter(type: ContentType, categoryId: String?): List<ContentItem> {
        val list = all(type)
        return if (categoryId == null) list else list.filter { it.categoryId == categoryId }
    }

    /** التصنيفات مع عدد العناصر في كل تصنيف */
    fun categoriesWithCounts(type: ContentType): List<CategoryCount> {
        val list = all(type)
        val byCat = list.groupingBy { it.categoryId }.eachCount()
        val catList = cats[type] ?: emptyList()
        val result = mutableListOf(CategoryCount(null, "__all__", list.size))
        for (c in catList) {
            val count = byCat[c.id] ?: 0
            if (count > 0) result.add(CategoryCount(c.id, c.name, count))
        }
        return result
    }

    /** أحدث العناصر المضافة */
    fun recentlyAdded(type: ContentType, n: Int): List<ContentItem> {
        return all(type).sortedByDescending { it.added ?: it.id.toLongOrNull() ?: 0L }.take(n)
    }

    fun clearAll() {
        items.clear()
        cats.clear()
    }
}
