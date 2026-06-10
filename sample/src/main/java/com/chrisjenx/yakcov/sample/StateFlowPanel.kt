package com.chrisjenx.yakcov.sample

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Data + UI for the live state-flow visualizer panels (see StateFlowSample.kt). One generic panel
 * renders either mechanism: 3 nodes / 2 labeled edges — same silhouette, different labels — plus a
 * pulse along the edge that just fired, the latest state as chips, and a short event ticker.
 */

/** Which arrow of the 2-edge pipeline a tick lights up: INPUT = node0->node1, COMMIT = node1->node2. */
enum class Edge { INPUT, COMMIT }

/** Chip color tone. */
enum class Tone { ERROR, WARNING, SUCCESS, NEUTRAL }

/** A labeled state box: [name] says WHAT it is (e.g. "severity"), [value] its current state. */
data class Chip(val name: String, val value: String, val tone: Tone)

/** One observed mutation, in panel-renderable form. */
data class FlowTick(
    val id: Int,              // monotonic — keys the pulse animation (newest wins)
    val edge: Edge,           // which arrow lights up
    val tickerLine: String,   // e.g. "onValueChange(\"a@\") -> ERROR, hidden"
    val chips: List<Chip>,    // severity / showError / draft / identity
)

/** Snapshot-backed holder of the last [MAX_TICKS] ticks, newest first. One per panel. */
@Stable
class TickFeed {
    private var nextId = 0
    val ticks = mutableStateListOf<FlowTick>()

    fun emit(edge: Edge, tickerLine: String, chips: List<Chip>) {
        ticks.add(0, FlowTick(nextId++, edge, tickerLine, chips))
        while (ticks.size > MAX_TICKS) ticks.removeAt(ticks.lastIndex)
    }

    companion object {
        const val MAX_TICKS = 4
    }
}

private const val PULSE_MILLIS = 700

/**
 * The hybrid visualizer: compact flow diagram (pulse + glow on the edge/node that just fired),
 * latest-state chips, and a [TickFeed.MAX_TICKS]-row ticker. Newest-wins pulse: a tick arriving
 * mid-animation restarts it — fast typing can't queue a backlog; the ticker catches every tick.
 */
@Composable
fun StateFlowPanel(
    title: String,
    nodes: List<String>,      // exactly 3
    edgeLabels: List<String>, // exactly 2 — label of the arrow between node i and node i+1
    ticks: List<FlowTick>,
    modifier: Modifier = Modifier,
) {
    require(nodes.size == 3 && edgeLabels.size == 2) { "Panel renders 3 nodes / 2 edges" }
    val latest = ticks.firstOrNull()
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(latest?.id) {
        if (latest != null) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, animationSpec = tween(durationMillis = PULSE_MILLIS))
        }
    }
    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)

        // Diagram: node boxes joined by labeled arrows; scrollable as a narrow-screen safety net.
        // The pulse CASCADES along every edge from the event's start node all the way to the UI
        // node, so you watch the change flow through to the UI rather than stop halfway. A typed
        // change (INPUT) sweeps both edges; a focus-loss/submit (COMMIT) sweeps from the engine to the UI.
        val pulsing = latest != null && pulse.value < 1f
        val startEdge = latest?.edge?.ordinal ?: 0
        val sweptCount = (nodes.lastIndex - startEdge).coerceAtLeast(1)
        val sweptPos = pulse.value * sweptCount
        val activeSeg = sweptPos.toInt().coerceIn(0, sweptCount - 1)
        val activeEdge = startEdge + activeSeg
        val edgeProgress = (sweptPos - activeSeg).coerceIn(0f, 1f)
        // One travelling highlight: the node the dot is leaving (first half) then arriving at.
        val hotNode = if (!pulsing) -1 else activeEdge + if (edgeProgress > 0.5f) 1 else 0
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            nodes.forEachIndexed { i, node ->
                NodeBox(text = node, hot = i == hotNode)
                if (i < nodes.lastIndex) EdgeArrow(
                    label = edgeLabels[i],
                    active = pulsing && i == activeEdge,
                    progress = edgeProgress,
                )
            }
        }

        // Latest state as chips.
        latest?.let { tick ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                tick.chips.forEach { ChipView(it) }
            }
        }

        // Ticker: newest on top, highlighted.
        Column(modifier = Modifier.animateContentSize()) {
            ticks.forEachIndexed { index, tick ->
                Text(
                    text = tick.tickerLine,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (index == 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun NodeBox(text: String, hot: Boolean) {
    val color = if (hot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Text(
        text = text,
        fontSize = 9.sp,
        maxLines = 1,
        color = color,
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun EdgeArrow(label: String, active: Boolean, progress: Float) {
    val track = 26.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 2.dp),
    ) {
        Text(
            text = label,
            fontSize = 7.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.outline,
        )
        Box(modifier = Modifier.width(track).height(8.dp)) {
            HorizontalDivider(modifier = Modifier.align(Alignment.Center))
            if (active) Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (track - 6.dp) * progress)
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@Composable
private fun ChipView(chip: Chip) {
    val tint = when (chip.tone) {
        Tone.ERROR -> MaterialTheme.colorScheme.error
        Tone.WARNING -> Color(0xFFB8860B)
        Tone.SUCCESS -> Color(0xFF2E7D32)
        Tone.NEUTRAL -> MaterialTheme.colorScheme.outline
    }
    // "name value" — dim name says WHAT the box is; the tinted value is its current state.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = chip.name,
            fontSize = 9.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = chip.value, fontSize = 9.sp, maxLines = 1, color = tint)
    }
}
