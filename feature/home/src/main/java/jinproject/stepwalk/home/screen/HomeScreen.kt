package jinproject.stepwalk.home.screen

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jinproject.stepwalk.design.component.DefaultLayout
import jinproject.stepwalk.design.component.VerticalSpacer
import jinproject.stepwalk.design.theme.StepWalkTheme
import jinproject.stepwalk.domain.model.METs
import jinproject.stepwalk.home.HealthConnector
import jinproject.stepwalk.home.screen.component.HomeTopAppBar
import jinproject.stepwalk.home.screen.component.page.UserPager
import jinproject.stepwalk.home.screen.state.Day
import jinproject.stepwalk.home.screen.state.Time
import jinproject.stepwalk.home.screen.state.Week
import jinproject.stepwalk.home.screen.state.Year
import jinproject.stepwalk.home.service.StepService
import jinproject.stepwalk.home.utils.onKorea
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
internal fun HomeScreen(
    context: Context = LocalContext.current,
    healthConnector: HealthConnector,
    homeViewModel: HomeViewModel = hiltViewModel(),
    navigateToCalendar: (Long) -> Unit,
) {
    val permissionState = rememberSaveable { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(contract = PermissionController.createRequestPermissionResultContract()) { result ->
            if (HealthConnector.healthPermissions.containsAll(result)) {
                Log.d("test", "권한 수락")
            } else {
                Log.d("test", "권한 거부")
            }
            permissionState.value = !permissionState.value
        }

    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val stepThisHour by homeViewModel.stepThisHour.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.time, permissionState.value) {
        if (healthConnector.checkPermissions()) {
            Log.d("test", "권한 있음")
            context.startForegroundService(Intent(context, StepService::class.java))
            val instant = Instant.now().onKorea()

            /*(0..23).forEach { count ->
                healthConnector.insertSteps(
                    step = count * 100L + 100L,
                    startTime = instant.truncatedTo(ChronoUnit.DAYS).toInstant().plus(count.toLong(), ChronoUnit.HOURS),
                    endTime = instant.truncatedTo(ChronoUnit.DAYS).toInstant().plus(count.toLong(), ChronoUnit.HOURS).plus(30L,ChronoUnit.MINUTES)
                )
            }

            (0..23).forEach { count ->
                healthConnector.insertHeartRates(
                    heartRate = (count % 4) * 40L,
                    startTime = instant.truncatedTo(ChronoUnit.DAYS).toInstant().plus(count.toLong(), ChronoUnit.HOURS),
                    endTime = instant.truncatedTo(ChronoUnit.DAYS).toInstant().plus(count.toLong(), ChronoUnit.HOURS).plus(30L,ChronoUnit.MINUTES)
                )
            }*/


            val endTime = instant
                .withHour(23)
                .withMinute(59)

            when (val time = uiState.time) {
                Day -> {
                    homeViewModel::setSteps.invoke(
                        healthConnector.readStepsByHours(
                            startTime = instant
                                .truncatedTo(ChronoUnit.DAYS)
                                .toLocalDateTime(),
                            endTime = endTime.toLocalDateTime(),
                            type = METs.Walk,
                            duration = Duration.ofHours(1L)
                        )
                    )

                    homeViewModel::setHeartRates.invoke(
                        healthConnector.readHeartRatesByHours(
                            startTime = instant
                                .truncatedTo(ChronoUnit.DAYS)
                                .toLocalDateTime(),
                            endTime = endTime.toLocalDateTime(),
                            duration = Duration.ofHours(1L)
                        )
                    )
                }

                else -> {
                    val startTime = when (time) {
                        Year -> instant
                            .minusMonths(instant.month.value.toLong() - 1)
                            .minusDays(instant.dayOfMonth.toLong() - 1)

                        Week -> instant
                            .minusDays(time.toNumberOfDays().toLong() - 1)

                        else -> instant.minusDays(instant.dayOfMonth.toLong() - 1)
                    }
                        .truncatedTo(ChronoUnit.DAYS)

                    homeViewModel::setSteps.invoke(
                        healthConnector.readStepsByPeriods(
                            startTime = startTime.toLocalDateTime(),
                            endTime = endTime.toLocalDateTime(),
                            type = METs.Walk,
                            period = time.toPeriod()
                        )
                    )

                    homeViewModel::setHeartRates.invoke(
                        healthConnector.readHeartRatesByPeriods(
                            startTime = startTime.toLocalDateTime(),
                            endTime = endTime.toLocalDateTime(),
                            period = time.toPeriod()
                        )
                    )
                }
            }
        } else {
            Log.d("test", "권한 없음")
            permissionLauncher.launch(HealthConnector.healthPermissions)
        }
    }

    HomeScreen(
        uiState = uiState,
        stepThisHour = stepThisHour,
        setTimeOnGraph = homeViewModel::setTime,
        navigateToCalendar = navigateToCalendar
    )
}

@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    stepThisHour: Int,
    context: Context = LocalContext.current,
    setTimeOnGraph: (Time) -> Unit,
    navigateToCalendar: (Long) -> Unit,
) {
    val popUpState = remember {
        mutableStateOf(false)
    }

    DefaultLayout(
        modifier = Modifier,
        contentPaddingValues = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        topBar = {
            HomeTopAppBar(
                modifier = Modifier,
                onClickTimeIcon = { popUpState.value = true },
                onClickIcon1 = {
                    val firstInstallTime = context.packageManager.getPackageInfo(
                        context.packageName,
                        0
                    ).firstInstallTime
                    navigateToCalendar(
                        Instant.ofEpochMilli(firstInstallTime)
                            .minus(30L, ChronoUnit.DAYS).epochSecond
                    )
                },
                onClickIcon2 = {}
            )
        },
    ) {
        UserPager(
            modifier = Modifier.fillMaxSize(),
            uiState = uiState,
        )
        PopUpWindow(
            popUpState = popUpState.value,
            offPopUp = { popUpState.value = false },
            onClickPopUpItem = { time -> setTimeOnGraph(time) }
        )
    }
}

@Composable
private fun PopUpWindow(
    popUpState: Boolean,
    offPopUp: () -> Unit,
    onClickPopUpItem: (Time) -> Unit
) {
    val transitionState = remember {
        MutableTransitionState(false)
    }
    transitionState.targetState = popUpState
    val transition = updateTransition(transitionState, label = "PopupTransition")

    val scale by transition.animateFloat(
        transitionSpec = {
            if (false isTransitioningTo true) {
                tween(durationMillis = 300)
            } else {
                tween(durationMillis = 250)
            }
        },
        label = "PopupScale"
    ) {
        if (it) {
            1f
        } else {
            0f
        }
    }

    val alpha by transition.animateFloat(
        transitionSpec = {
            if (false isTransitioningTo true) {
                tween(durationMillis = 300)
            } else {
                tween(durationMillis = 250)
            }
        },
        label = "PopupAlpha"
    ) {
        if (it) {
            1f
        } else {
            0f
        }
    }

    if (transitionState.currentState || transitionState.targetState) {
        Popup(
            popupPositionProvider = object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset {
                    return IntOffset(
                        x = 20,
                        y = 200
                    )
                }
            },
            properties = PopupProperties(focusable = true),
            onDismissRequest = offPopUp
        ) {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .width(100.dp)
                    .shadow(5.dp, RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Time.values.forEachIndexed { index, time ->
                    Text(
                        text = time.display(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onClickPopUpItem(time)
                            }
                    )
                    if (index != Time.values.lastIndex) {
                        VerticalSpacer(height = 10.dp)
                    }
                }
            }
        }
    }
}

@Composable
@Preview
private fun PreviewHomeScreen() = StepWalkTheme {
    HomeScreen(
        uiState = HomeUiState.getInitValues(),
        stepThisHour = 100,
        setTimeOnGraph = {},
        navigateToCalendar = {}
    )
}