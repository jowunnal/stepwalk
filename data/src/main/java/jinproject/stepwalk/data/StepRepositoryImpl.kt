package jinproject.stepwalk.data

import androidx.datastore.core.DataStore
import jinproject.stepwalk.domain.model.StepData
import jinproject.stepwalk.domain.repository.StepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class StepRepositoryImpl @Inject constructor(
    private val prefs: DataStore<StepWalkPreferences>
) : StepRepository {

    private val data = prefs.data
        .catch { exception ->
            if (exception is IOException) {
                emit(StepWalkPreferences.getDefaultInstance())
            } else {
                throw exception
            }
        }

    override fun getStep(): Flow<StepData> = data.map { prefs ->
        StepData(
            current = prefs.today,
            last = prefs.last,
            yesterday = prefs.yesterday
        )
    }

    override suspend fun setTodayStep(today: Long) {
        prefs.updateData { pref ->
            pref
                .toBuilder()
                .setToday(today)
                .build()
        }
    }

    override suspend fun setYesterdayStep(yesterday: Long) {
        prefs.updateData { pref ->
            pref
                .toBuilder()
                .setYesterday(yesterday)
                .build()
        }
    }

    override suspend fun setLastStep(last: Long) {
        prefs.updateData { pref ->
            pref
                .toBuilder()
                .setLast(last)
                .build()
        }
    }
}