package id.novamusic.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.novaStore by preferencesDataStore(name = "nova_settings")

class NovaPreferences(private val context: Context) {
    private val repeatKey = intPreferencesKey("repeat")
    private val shuffleKey = booleanPreferencesKey("shuffle")
    private val speedKey = floatPreferencesKey("speed")
    private val queueKey = stringPreferencesKey("queue")
    private val sortKey = stringPreferencesKey("sort")
    private val reduceMotionKey = booleanPreferencesKey("reduce_motion")
    private val themeKey = stringPreferencesKey("theme")
    private val uiStyleKey = stringPreferencesKey("ui_style")
    private val sleepDeadlineKey = longPreferencesKey("sleep_deadline_epoch_ms")
    private val volumeKey = floatPreferencesKey("volume")

    val repeat: Flow<Int> = context.novaStore.data.map { it[repeatKey] ?: 0 }
    val shuffle: Flow<Boolean> = context.novaStore.data.map { it[shuffleKey] ?: false }
    val speed: Flow<Float> = context.novaStore.data.map { it[speedKey] ?: 1f }
    val queue: Flow<String> = context.novaStore.data.map { it[queueKey] ?: "" }
    val sort: Flow<String> = context.novaStore.data.map { it[sortKey] ?: "Title" }
    val reduceMotion: Flow<Boolean> = context.novaStore.data.map { it[reduceMotionKey] ?: false }
    val theme: Flow<String> = context.novaStore.data.map { it[themeKey] ?: "Daylight Glass" }
    val uiStyle: Flow<String> = context.novaStore.data.map { it[uiStyleKey] ?: "Glass" }
    val sleepDeadline: Flow<Long> = context.novaStore.data.map { it[sleepDeadlineKey] ?: 0L }
    val volume: Flow<Float> = context.novaStore.data.map { (it[volumeKey] ?: 1f).coerceIn(0f, 1f) }

    suspend fun setRepeat(v: Int) { context.novaStore.edit { it[repeatKey] = v } }
    suspend fun setShuffle(v: Boolean) { context.novaStore.edit { it[shuffleKey] = v } }
    suspend fun setSpeed(v: Float) { context.novaStore.edit { it[speedKey] = v } }
    suspend fun setQueue(v: String) { context.novaStore.edit { it[queueKey] = v } }
    suspend fun setSort(v: String) { context.novaStore.edit { it[sortKey] = v } }
    suspend fun setReduceMotion(v: Boolean) { context.novaStore.edit { it[reduceMotionKey] = v } }
    suspend fun setTheme(v: String) { context.novaStore.edit { it[themeKey] = v } }
    suspend fun setUiStyle(v: String) { context.novaStore.edit { it[uiStyleKey] = v } }
    suspend fun setSleepDeadline(v: Long) { context.novaStore.edit { if (v > 0L) it[sleepDeadlineKey] = v else it.remove(sleepDeadlineKey) } }
    suspend fun setVolume(v: Float) { context.novaStore.edit { it[volumeKey] = v.coerceIn(0f, 1f) } }
}
