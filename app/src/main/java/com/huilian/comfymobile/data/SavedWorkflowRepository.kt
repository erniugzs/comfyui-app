package com.huilian.comfymobile.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.huilian.comfymobile.data.models.SavedWorkflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class SavedWorkflowRepository(private val context: Context) {
    private val gson = Gson()
    private val workflowsDir: File by lazy {
        File(context.filesDir, "workflows").apply { mkdirs() }
    }
    private val workflowsFile: File by lazy {
        File(context.filesDir, "saved_workflows_v1.json")
    }

    suspend fun save(workflow: SavedWorkflow): Result<SavedWorkflow> = withContext(Dispatchers.IO) {
        try {
            val list = loadAll().toMutableList()
            val index = list.indexOfFirst { it.id == workflow.id }
            val updated = workflow.copy(updatedAt = System.currentTimeMillis())
            if (index >= 0) {
                list[index] = updated
            } else {
                list.add(updated)
            }
            writeAll(list)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadAll(): List<SavedWorkflow> = withContext(Dispatchers.IO) {
        try {
            if (!workflowsFile.exists()) return@withContext emptyList()
            val type = object : TypeToken<List<SavedWorkflow>>() {}.type
            gson.fromJson<List<SavedWorkflow>>(workflowsFile.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val list = loadAll().toMutableList().filter { it.id != id }
            writeAll(list)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rename(id: String, newName: String): Result<SavedWorkflow> = withContext(Dispatchers.IO) {
        try {
            val list = loadAll().toMutableList()
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                val updated = list[index].copy(name = newName, updatedAt = System.currentTimeMillis())
                list[index] = updated
                writeAll(list)
                Result.success(updated)
            } else {
                Result.failure(Exception("工作流不存在"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleStar(id: String): Result<SavedWorkflow> = withContext(Dispatchers.IO) {
        try {
            val list = loadAll().toMutableList()
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                val updated = list[index].copy(isStarred = !list[index].isStarred)
                list[index] = updated
                writeAll(list)
                Result.success(updated)
            } else {
                Result.failure(Exception("工作流不存在"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getById(id: String): SavedWorkflow? = withContext(Dispatchers.IO) {
        loadAll().find { it.id == id }
    }

    private fun writeAll(list: List<SavedWorkflow>) {
        workflowsFile.writeText(gson.toJson(list))
    }
}
