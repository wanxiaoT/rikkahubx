package me.rerere.rikkahub.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import me.rerere.rikkahub.data.model.ProcessingStatus
import me.rerere.rikkahub.data.repository.KnowledgeRepository
import me.rerere.rikkahub.service.KnowledgeService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

private const val TAG = "KnowledgeProcessingWorker"
private const val KEY_ITEM_ID = "item_id"
private const val KEY_BASE_ID = "base_id"

/**
 * Worker for processing knowledge items in the background
 */
class KnowledgeProcessingWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val knowledgeService: KnowledgeService by inject()
    private val knowledgeRepository: KnowledgeRepository by inject()

    override suspend fun doWork(): Result {
        val itemId = inputData.getString(KEY_ITEM_ID)
        val baseId = inputData.getString(KEY_BASE_ID)

        if (itemId == null || baseId == null) {
            Log.e(TAG, "Missing item_id or base_id in worker input")
            return Result.failure()
        }

        return try {
            val uuid = Uuid.parse(itemId)
            val item = knowledgeRepository.getItemById(uuid)

            if (item == null) {
                Log.e(TAG, "Item not found: $itemId")
                return Result.failure()
            }

            if (item.status == ProcessingStatus.COMPLETED) {
                Log.d(TAG, "Item already processed: $itemId")
                return Result.success()
            }

            Log.d(TAG, "Processing item: $itemId")
            val result = knowledgeService.processItem(item)

            if (result.isSuccess) {
                Log.d(TAG, "Successfully processed item: $itemId")
                Result.success()
            } else {
                Log.e(TAG, "Failed to process item: $itemId", result.exceptionOrNull())
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing item: $itemId", e)
            Result.retry()
        }
    }

    companion object {
        /**
         * Schedule processing for a specific knowledge item
         */
        fun enqueue(context: Context, itemId: Uuid, baseId: Uuid) {
            val inputData = Data.Builder()
                .putString(KEY_ITEM_ID, itemId.toString())
                .putString(KEY_BASE_ID, baseId.toString())
                .build()

            val workRequest = OneTimeWorkRequestBuilder<KnowledgeProcessingWorker>()
                .setInputData(inputData)
                .addTag("knowledge_processing")
                .addTag("item_$itemId")
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "Enqueued processing for item: $itemId")
        }

        /**
         * Process all pending items in a knowledge base
         */
        fun enqueueAllPending(context: Context, baseId: Uuid, items: List<Uuid>) {
            items.forEach { itemId ->
                enqueue(context, itemId, baseId)
            }
            Log.d(TAG, "Enqueued ${items.size} items for processing")
        }
    }
}
