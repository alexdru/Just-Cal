package com.justcal.app.data

import androidx.room.withTransaction
import com.justcal.app.domain.*
import com.justcal.app.domain.Retention
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Singleton
class RoomFoodCatalogRepository @Inject constructor(private val database: DiaryDatabase) : FoodCatalogRepository {
    private val dao get() = database.foodCatalogDao()
    private var historyCursor = 0L

    override suspend fun buildHistoryBatch(): Boolean = withContext(Dispatchers.IO) {
        database.withTransaction {
            if (dao.staleProjections(FoodMatcher.VERSION) > 0) { dao.invalidate(); historyCursor = 0 }
            var history = dao.unindexedHistory(historyCursor)
            if (history.isEmpty() && historyCursor != 0L) {
                historyCursor = 0
                history = dao.unindexedHistory(historyCursor)
            }
            historyCursor = history.lastOrNull()?.id ?: 0L
            val revisions = dao.unindexedRevisions()
            history.forEach { currentCoroutineContext().ensureActive(); indexEntry(it) }
            revisions.forEach { currentCoroutineContext().ensureActive(); indexRevision(it.domain()) }
            history.size == 100 || revisions.size == 100
        }
    }

    private fun historyName(name: String) = Normalizer.normalize(name, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N}%.,]+"), " ").trim()
    private fun historyKey(row: DiaryEntryEntity): String {
        val identity = listOf(historyName(row.name), row.energyKcalHundredths, row.proteinGramsHundredths,
            row.fatGramsHundredths, row.carbsGramsHundredths).joinToString("\u0000")
        return "H:" + MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
    internal suspend fun indexDiary(row: DiaryEntryEntity) { dao.removeMember(row.portableId); indexEntry(row) }
    private suspend fun indexEntry(row: DiaryEntryEntity) {
        val key = historyKey(row)
        index(FoodSearchEntity(key, "H", historyName(row.name), row.name, row.energyKcalHundredths,
            row.proteinGramsHundredths, row.fatGramsHundredths, row.carbsGramsHundredths, FoodMatcher.VERSION))
        dao.member(FoodHistoryMember(row.portableId, key))
    }
    private suspend fun indexRevision(r: ProductRevision) = index(FoodSearchEntity("R:" + r.id, "R",
        historyName(r.name), r.name, r.energy, r.protein, r.fat, r.carbs, FoodMatcher.VERSION))
    private suspend fun index(row: FoodSearchEntity) {
        dao.projection(row)
        dao.tokens(FoodMatcher.normalizeName(row.name).tokens.distinct().map { FoodTokenEntity(it, row.key) })
    }

    override suspend fun candidate(key: String): FoodCandidate? = withContext(Dispatchers.IO) {
        if (key.startsWith("R:")) {
            val r = dao.revision(key.removePrefix("R:"))?.domain() ?: return@withContext null
            if (dao.product(r.productId)?.archived != false) return@withContext null
            FoodCandidate(key, r)
        } else {
            val row = dao.projection(key) ?: return@withContext null
            val history = dao.history(key)
            val representative = history.entryId?.let { dao.entry(it) } ?: return@withContext null
            FoodCandidate(key, ProductRevision(key, key, name = representative.name, energy = row.energy,
                protein = row.protein, fat = row.fat, carbs = row.carbs, savedAtMillis = 0,
                origins = "DIARY_HISTORY"), history.entryId, history.count, history.lastDay)
        }
    }
    override suspend fun search(evidence: FoodMatchEvidence): FoodSearchResult = withContext(Dispatchers.IO) {
        val context = currentCoroutineContext()
        val started = System.nanoTime()
        val tokens = FoodMatcher.normalizeName(evidence.name).tokens.distinct()
        val exactR = if (tokens.isEmpty()) emptyList() else dao.retrieve(tokens, "R", 201)
        val exactH = if (tokens.isEmpty()) emptyList() else dao.retrieve(tokens, "H", 201)
        val expanded = linkedSetOf<String>()
        tokens.forEach { token ->
            context.ensureActive()
            if (token.length >= 5) {
                val prefix = token.take(1)
                dao.variants(prefix, prefix + '\uffff', token.length - 2, token.length + 2)
                    .filter { it !in tokens && FoodMatcher.tokenAgreement(token, it) > 0 }.forEach(expanded::add)
            }
        }
        val queryTokens = (tokens + expanded).distinct()
        suspend fun fuzzy(source: String) = if (expanded.isEmpty()) emptyList() else
            queryTokens.chunked(400).flatMap { dao.retrieve(it, source, 201) }.distinctBy { it.key }.take(201)
        fun range(index: Int): Pair<Long?, Long?> {
            val n = evidence.nutrients[index]
            val value = n.hundredths
            if (evidence.basis != MatchNutritionBasis.PER_100_G || !n.comparable || value == null || value == 0L) return null to null
            val v = value.toBigInteger()
            val tolerance = maxOf((if (index == 0) 200 else 30).toBigInteger(), v * (if (index == 0) 2 else 3).toBigInteger() / 100.toBigInteger())
            return maxOf(java.math.BigInteger.ZERO, v - tolerance).toLong() to minOf(Long.MAX_VALUE.toBigInteger(), v + tolerance).toLong()
        }
        val ranges = (0..3).map(::range)
        suspend fun nutrition(source: String): List<FoodSearchEntity> {
            if (ranges.count { it.first != null } < 2) return emptyList()
            return dao.retrieveNutrition(source, ranges[0].first, ranges[0].second, ranges[1].first,
                ranges[1].second, ranges[2].first, ranges[2].second, ranges[3].first, ranges[3].second)
        }
        val r = (exactR + fuzzy("R") + nutrition("R")).distinctBy { it.key }.take(201)
        val h = (exactH + fuzzy("H") + nutrition("H")).distinctBy { it.key }.take(201)
        val chosen = (r.take(100) + h.take(100)).toMutableList()
        (r.drop(100) + h.drop(100)).take(200 - chosen.size).forEach(chosen::add)
        val hydrated = database.withTransaction {
            val revisions = dao.revisionSnapshots(chosen.filter { it.source == "R" }.map { it.key.removePrefix("R:") })
                .map { FoodCandidate("R:" + it.id, it.domain()) }
            val history = dao.historySnapshots(chosen.filter { it.source == "H" }.map { it.key }).map { row ->
                val s = row.snapshot
                FoodCandidate(s.key, ProductRevision(s.key, s.key, name = s.name, energy = s.energy,
                    protein = s.protein, fat = s.fat, carbs = s.carbs, savedAtMillis = 0,
                    origins = "DIARY_HISTORY"), row.entryId, row.count, row.lastDay)
            }
            revisions + history
        }
        val retrieved = System.nanoTime()
        val scored = hydrated.map { candidate ->
            context.ensureActive()
            candidate.copy(match = FoodMatcher.match(candidate.key, evidence, candidate.revision.evidence()) { context.ensureActive() })
        }.sortedWith(compareBy<FoodCandidate> { it.match!!.category.ordinal }
            .thenByDescending { it.match!!.rankingScore }.thenByDescending { it.match!!.comparableFields }.thenBy { it.key })
        val best = scored.distinctBy { if (it.historyEntryUuid == null) it.revision.productId else it.key }
        FoodSearchResult(best, dao.unindexedHistory().isNotEmpty() || dao.unindexedRevisions().isNotEmpty(),
            r.size + h.size > chosen.size || expanded.size >= 50,
            (retrieved - started) / 1_000_000.0, (System.nanoTime() - retrieved) / 1_000_000.0)
    }
    override suspend fun revisions(productId: String, offset: Int): List<FoodCandidate> = withContext(Dispatchers.IO) {
        dao.revisions(productId, offset.coerceAtLeast(0)).map { FoodCandidate("R:" + it.id, it.domain()) }
    }
    override suspend fun duplicates(revision: ProductRevision): List<FoodCandidate> = withContext(Dispatchers.IO) {
        dao.duplicates(revision.name, revision.energy, revision.protein, revision.fat, revision.carbs)
            .filter { it.brand == revision.brand && it.manufacturer == revision.manufacturer && it.retailer == revision.retailer &&
                it.deliveryService == revision.deliveryService && it.packageMassHundredths == revision.packageMassHundredths }
            .map { FoodCandidate("R:" + it.id, it.domain()) }
    }

    override suspend fun save(entry: DiaryEntry, request: CatalogSave): Long = withContext(Dispatchers.IO) {
        listOf(request.entryUuid, request.productUuid, request.revisionUuid).forEach { id ->
            require(java.util.UUID.fromString(id).toString() == id) { "Invalid creation UUID" }
        }
        require(request.fieldOrigins.length in 1..1000 && '\u0000' !in request.fieldOrigins)
        require(request.provenance in setOf("USER_SELECTED", "HISTORY_PROMOTION", "SCAN_ACCEPTED", "USER_CORRECTION"))
        database.withTransaction {
            val diary = database.diaryDao()
            val existing = if (entry.id != 0L) checkNotNull(diary.get(entry.id)) else dao.entry(request.entryUuid)
            val uuid = existing?.portableId ?: request.entryUuid
            val row = DiaryEntryEntity.from(entry).copy(id = existing?.id ?: 0, portableId = uuid)
            // A retry of a committed create is accepted only for exactly the same diary payload.
            if (entry.id == 0L && existing != null) {
                check(existing == row) { "Creation identity already has a different payload" }
                val link = dao.entryLink(uuid)
                if (request.retention == Retention.NEW_PRODUCT || request.retention == Retention.NEW_REVISION) {
                    val committed = link?.revisionId?.let { dao.revision(it)?.domain() }
                    val parent = request.selectedRevision?.let { dao.revision(it)?.domain() }
                    check(committed != null && canonicalEqual(committed, revisionFrom(entry, request, parent))) {
                        "Creation identity has different catalog payload"
                    }
                }
                if (request.retention == Retention.REUSE) check(link?.revisionId == request.selectedRevision)
                if (request.retention == Retention.NEW_PRODUCT || request.retention == Retention.NEW_REVISION)
                    check(link?.revisionId == request.revisionUuid || link?.revisionId == request.selectedRevision)
                return@withTransaction existing.id
            }
            var selected = request.selectedRevision?.let { dao.revision(it)?.domain() }
            if (selected != null && dao.product(selected.productId)?.archived != false) selected = null
            // Disappearing selection preserves the draft as a diary-only snapshot.
            val retention = if (selected == null && request.retention in setOf(Retention.REUSE, Retention.NEW_REVISION))
                Retention.DIARY_ONLY else request.retention
            val proposed = if (retention == Retention.DIARY_ONLY || retention == Retention.REUSE) null else revisionFrom(entry, request, selected)
            val same = selected != null && proposed != null && canonicalEqual(selected, proposed)
            val revision = when (retention) {
                Retention.DIARY_ONLY -> null
                Retention.REUSE -> selected
                Retention.NEW_REVISION -> if (same) selected else proposed
                Retention.NEW_PRODUCT -> requireNotNull(proposed)
            }
            if (revision != null && dao.revision(revision.id) == null) {
                if (request.retention == Retention.NEW_PRODUCT) dao.insertProduct(ProductEntity(revision.productId, request.revisionSavedAtMillis ?: entry.createdAtMillis, false))
                dao.insertRevision(ProductRevisionEntity.from(revision))
                indexRevision(revision)
            } else if (revision != null) check(dao.revision(revision.id)?.domain() == revision)
            val id = if (existing == null) diary.insert(row) else {
                check(diary.update(row) == 1); existing.id
            }
            val previousLink = if (request.preserveExistingAssociation) dao.entryLink(uuid) else null
            dao.unlink(uuid)
            dao.removeMember(uuid)
            if (revision != null) dao.link(EntryProductLinkEntity(uuid, revision.id, request.provenance))
                else if (previousLink != null) dao.link(previousLink) else indexEntry(row.copy(id = id))
            id
        }
    }

    private fun revisionFrom(entry: DiaryEntry, request: CatalogSave, selected: ProductRevision?): ProductRevision {
        val d = request.details
        fun optional(text: String) = text.trim().takeIf { it.isNotEmpty() }?.also { require(it.length <= 200 && '\u0000' !in it) }
        val mass = d.packageGrams.trim().takeIf { it.isNotEmpty() }?.let {
            require(it.matches(Regex("[0-9]+([.,][0-9]{1,2})?")))
            it.replace(',', '.').toBigDecimal().movePointRight(2).longValueExact().also { n -> require(n > 0) }
        }
        return ProductRevision(request.revisionUuid,
            if (request.retention == Retention.NEW_REVISION) requireNotNull(selected).productId else request.productUuid,
            if (request.retention == Retention.NEW_REVISION) selected?.id else null,
            entry.name, entry.per100g.energyKcalHundredths, entry.per100g.proteinGramsHundredths,
            entry.per100g.fatGramsHundredths, entry.per100g.carbsGramsHundredths, request.revisionSavedAtMillis ?: entry.createdAtMillis,
            optional(d.brand), optional(d.manufacturer), optional(d.retailer), optional(d.deliveryService), mass, request.fieldOrigins)
    }
    private fun canonicalEqual(a: ProductRevision, b: ProductRevision) =
        a.copy(id = b.id, productId = b.productId, previousRevisionId = b.previousRevisionId,
            savedAtMillis = b.savedAtMillis, origins = b.origins) == b
    override suspend fun archive(productId: String) = withContext(Dispatchers.IO) { database.withTransaction { dao.removeProductProjections(productId); dao.archive(productId) } }
    override suspend fun forget(productId: String) = withContext(Dispatchers.IO) { database.withTransaction { dao.removeProductProjections(productId); dao.forget(productId) } }
}
