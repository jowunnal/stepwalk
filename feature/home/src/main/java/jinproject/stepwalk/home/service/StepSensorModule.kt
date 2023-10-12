package jinproject.stepwalk.home.service

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import jinproject.stepwalk.home.receiver.AlarmReceiver
import jinproject.stepwalk.home.utils.setInExactRepeating
import jinproject.stepwalk.home.utils.onKorea
import jinproject.stepwalk.home.worker.StepInsertWorker
import java.time.LocalDateTime
import java.util.Calendar
import java.util.Timer

internal class StepSensorModule(
    private val context: Context,
    private val steps: Array<Long>,
    onSensorChanged: (Long, Long) -> Unit
) {
    private val sensorManager: SensorManager by lazy { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val stepSensor: Sensor? by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }

    private val alarmManager: AlarmManager by lazy { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }

    var startTime: LocalDateTime = LocalDateTime.now()
    var endTime: LocalDateTime = LocalDateTime.now()

    private val stepListener: SensorEventListener by lazy {
        object : SensorEventListener {
            override fun onSensorChanged(p0: SensorEvent?) {
                val step = p0?.values?.first()?.toLong() ?: 0L

                val today = when (step) {
                    0L -> {
                        steps.apply {
                            steps[1] = 0L
                        }

                        steps.first()
                    }

                    else -> {
                        when {
                            steps.first() == 0L && steps[1] == 0L -> {
                                steps[1] = step
                            }
                        }
                        step - steps[1]
                    }
                }

                onSensorChanged(today, steps[1])
                setWorkOnStep(today)
            }

            override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}

        }
    }

    init {
        registerSensor()
        alarmUpdatingLastStep()
    }

    private fun alarmUpdatingLastStep() {
        val time = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        alarmManager.setInExactRepeating(
            context = context,
            notifyIntent = {
                Intent(context, AlarmReceiver::class.java)
            },
            type = AlarmManager.RTC_WAKEUP,
            time = time.timeInMillis,
            interval = AlarmManager.INTERVAL_DAY
        )

    }

    private fun registerSensor() {
        sensorManager.registerListener(stepListener, stepSensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun unRegisterSensor() {
        sensorManager.unregisterListener(stepListener, stepSensor)
    }

    private fun setWorkOnStep(todayStep: Long) {
        when {
            (LocalDateTime.now().onKorea().toEpochSecond() - endTime.onKorea()
                .toEpochSecond()) < 60 -> {
                endTime = LocalDateTime.now()
            }

            else -> {
                val workRequest = OneTimeWorkRequestBuilder<StepInsertWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setInputData(
                        Data
                            .Builder()
                            .putLong(Key.DISTANCE.value, todayStep - steps[2])
                            .putLong(Key.START.value, startTime.onKorea().toEpochSecond())
                            .putLong(Key.END.value, endTime.onKorea().toEpochSecond())
                            .putLong(Key.STEP_LAST_TIME.value, todayStep)
                            .build()
                    )
                    .build()

                WorkManager
                    .getInstance(context)
                    .enqueueUniqueWork(
                        "insertStepWork",
                        ExistingWorkPolicy.REPLACE,
                        workRequest
                    )

                startTime = LocalDateTime.now()
                endTime = LocalDateTime.now()
            }
        }
    }

    enum class Key(val value: String) {
        DISTANCE("distance"),
        START("start"),
        END("end"),
        STEP_LAST_TIME("stepLastTime"),
        YESTERDAY("yesterday")
    }
}