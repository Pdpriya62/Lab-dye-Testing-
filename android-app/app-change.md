# App change: camera card on the Live screen

Status: built 2026-11-12. 46 unit tests pass (11 new in `CamParserTest`), lint 0 errors, checked on the emulator: card, 3×3 and 4×4 grids, no-target state, malformed payloads (no crash). Written 2026-11-12 for the demo on 2026-11-13.

## Goal
Show the camera station's result in the app, next to the TCS34725 reading:
- camera colour swatch against the target, with ΔE2000 and Pass / Marginal / Fail
- a levelness grid (3×3 by default): each cell drawn in its own measured colour with its ΔE2000; the word "Uniform" or "Uneven" with an icon
- camera vs sensor ΔE2000 (agreement check)
- a warning when the camera result is for a different sample than the live reading, or is old

The photo itself stays on the laptop (`images/`). The app gets numbers only, over MQTT. That avoids cleartext HTTP to the ESP32-CAM, which the network security config blocks, and keeps the release TLS rule intact.

## What is already done (outside the app)
- `camera/cam_capture.py --mqtt` publishes one JSON message per capture on `rvu/team11/labdip/cam` (sibling of the data topic, QoS 1, **not retained**). Verified end to end on a test topic: 592 bytes for a 3×3 grid.
- It takes the sample ID and target from the rig's data topic, so what you set in the app's Rig controls applies to the camera too.
- Firmware: the TCS34725 reading is now the median of 3 reads (steadier ΔE). JSON format unchanged, so the app needs no change for that.

## MQTT contract: `.../cam`
Example (3×3 grid, target and rig reading available):
```json
{"sample_id":"S7","timestamp":"2026-11-12T14:51:36","L":82.13,"a":-2.95,"b":14.58,"R":210,"G":205,"B":177,
 "grid":3,"cells":[[82.4,-3.1,15.2,0.71],null,[81.9,-2.8,14.1,0.6], "...9 entries in total"],
 "roi":"auto","white":"flat","levelness_max":2.57,"levelness_mean":1.18,"level_tol":1.0,"uniform":false,
 "target_L":82.0,"target_a":-3.0,"target_b":15.0,"deltaE2000":0.27,
 "sensor_L":80.5,"sensor_a":-3.1,"sensor_b":18.2,"cam_vs_sensor_dE2000":2.38}
```

| Field | Type | Always? | Valid range / values |
|---|---|---|---|
| `sample_id` | string | yes | whitelist `[A-Za-z0-9 _.-]`, ≤ 24 chars (use `ReadingParser.cleanText`) |
| `timestamp` | string | yes | laptop local time, ISO `YYYY-MM-DDTHH:MM:SS`; display only |
| `L`, `a`, `b` | number | yes | L 0–100, a/b −128–128 |
| `R`, `G`, `B` | int | yes | 0–255, white-corrected camera colour; use for the swatch |
| `grid` | int | yes | 2, 3 or 4 |
| `cells` | array | yes | exactly `grid*grid` entries, row-major from top-left. Each is `null` (cell mostly off the patch) or `[L, a, b, dE2000]` |
| `roi` | string | yes | `auto` (patch found) or `centre` (fallback) |
| `white` | string | yes | `none`, `manual`, `auto`, `flat` |
| `levelness_max`, `levelness_mean` | number | when ≥ 1 cell | 0–100 |
| `level_tol` | number | with levelness | 0–100 (default 1.0) |
| `uniform` | bool | with levelness | `levelness_max <= level_tol` |
| `target_L/a/b`, `deltaE2000` | number | when a target is known | ranges as Lab; ΔE 0–300 |
| `sensor_L/a/b`, `cam_vs_sensor_dE2000` | number | when the rig sent a reading in the last 15 s | ranges as Lab; ΔE 0–300 |

Treat it as untrusted like every other payload: size cap 2048 B, strict decode, range checks, whitelist, drop and count on any failure, never crash.

## Decision to confirm (CLAUDE.md says ask when docs disagree)
`CLAUDE.md` says Room is the single source of truth. For tonight, keep the **latest camera result in memory only** (a `StateFlow` in a small `CameraRepository`). No Room table, no schema migration, no history.
- Why: one value, demo tomorrow, a Room migration is the riskiest part of this change.
- Cost: the camera card is empty after an app restart until the next capture. Mark it in code: `// shortcut: camera result kept in memory only, add a Room table if camera history is needed`.
- Note it in `architecture.md` as a deliberate exception.

## Build order (about 3–4 h)
| # | Step | Time | Must for demo |
|---|---|---|---|
| 1 | Topic helper + `CamResult` + `CamParser` + tests | 45 min | yes |
| 2 | `CameraRepository`, MQTT routing, `AppContainer` | 30 min | yes |
| 3 | `LiveViewModel` / `LiveUiState` | 20 min | yes |
| 4 | `CameraCard` + `LevelnessGrid` on Live, previews | 1.5 h | yes |
| 5 | Manual test with `cam_capture.py --file … --mqtt` | 20 min | yes |
| 6 | Docs: `progress.md`, `architecture.md`, `explanation.md` | 15 min | yes |
| 7 | Nice to have: camera row in Samples detail, bigger Pass/Fail chip in the Live header | — | no |

Stop after step 6 if it is late. Step 7 is optional.

## Step 1: model, parser, topic

`data/mqtt/RigCommands.kt`, next to the other helpers:
```kotlin
fun camTopic(dataTopic: String) = sibling(dataTopic, "cam")
```

New `data/model/CamResult.kt`:
```kotlin
@Serializable
data class CamResult(
    @SerialName("sample_id") val sampleId: String,
    val timestamp: String,
    @SerialName("L") val labL: Double,
    @SerialName("a") val labA: Double,
    @SerialName("b") val labB: Double,
    @SerialName("R") val red: Int,
    @SerialName("G") val green: Int,
    @SerialName("B") val blue: Int,
    val grid: Int,
    val cells: List<List<Double>?>,
    val roi: String,
    val white: String,
    @SerialName("levelness_max") val levelnessMax: Double? = null,
    @SerialName("levelness_mean") val levelnessMean: Double? = null,
    @SerialName("level_tol") val levelTol: Double? = null,
    val uniform: Boolean? = null,
    @SerialName("target_L") val targetL: Double? = null,
    @SerialName("target_a") val targetA: Double? = null,
    @SerialName("target_b") val targetB: Double? = null,
    val deltaE2000: Double? = null,
    @SerialName("sensor_L") val sensorL: Double? = null,
    @SerialName("sensor_a") val sensorA: Double? = null,
    @SerialName("sensor_b") val sensorB: Double? = null,
    @SerialName("cam_vs_sensor_dE2000") val camVsSensor: Double? = null,
)

/** Received camera result plus the phone time it arrived (the laptop clock may differ). */
data class CamShot(val result: CamResult, val receivedAt: Long)
```

New `data/model/CamParser.kt`:
```kotlin
/** Validates untrusted `.../cam` payloads from cam_capture.py. Never throws. */
object CamParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }
    private val rois = setOf("auto", "centre")
    private val whites = setOf("none", "manual", "auto", "flat")

    fun parse(bytes: ByteArray): Result<CamResult> = runCatching {
        require(bytes.size <= ReadingParser.MAX_BYTES) { "payload too large: ${bytes.size} B" }
        val c = json.decodeFromString<CamResult>(bytes.decodeToString())
        lab(c.labL, c.labA, c.labB)
        listOf(c.red, c.green, c.blue).forEach { require(it in 0..255) { "RGB out of range" } }
        require(c.grid in 2..4) { "grid ${c.grid}" }
        require(c.cells.size == c.grid * c.grid) { "cells ${c.cells.size} for grid ${c.grid}" }
        c.cells.forEach { cell ->
            if (cell != null) {
                require(cell.size == 4) { "cell needs 4 numbers" }
                lab(cell[0], cell[1], cell[2]); de(cell[3])
            }
        }
        require(c.roi in rois && c.white in whites) { "bad roi/white" }
        listOf(c.levelnessMax, c.levelnessMean, c.levelTol).forEach { it?.let { v -> require(v.isFinite() && v in 0.0..100.0) } }
        c.deltaE2000?.let(::de); c.camVsSensor?.let(::de)
        if (c.targetL != null || c.targetA != null || c.targetB != null) lab(c.targetL!!, c.targetA!!, c.targetB!!)
        if (c.sensorL != null || c.sensorA != null || c.sensorB != null) lab(c.sensorL!!, c.sensorA!!, c.sensorB!!)
        require(c.timestamp.length <= 32)
        c.copy(sampleId = ReadingParser.cleanText(c.sampleId) ?: "?")
    }

    private fun lab(l: Double, a: Double, b: Double) {
        require(l.isFinite() && l in 0.0..100.0) { "L out of range" }
        require(a.isFinite() && a in -128.0..128.0 && b.isFinite() && b in -128.0..128.0) { "a/b out of range" }
    }

    private fun de(v: Double) = require(v.isFinite() && v in 0.0..300.0) { "deltaE out of range" }
}
```
A partial target (one of the three missing) makes `!!` throw inside `runCatching`, so it becomes a dropped message, not a crash. That is intended.

New `test/.../CamParserTest.kt`, one test each:
- the example payload above (with 9 real cells) parses; `sampleId`, `cells[1] == null`, `deltaE2000` correct
- minimal payload without the optional fields parses
- oversize (> 2048 B) is rejected
- `grid` 5 is rejected; `cells` count 8 for grid 3 is rejected; a cell with 3 numbers is rejected
- L 120, a −200, R 300, `NaN` (send as a string, decode fails) are rejected
- `roi` `"x"` is rejected
- `sample_id` `"S1;rm"` comes back as `"?"`
- target with only `target_L` is rejected
- not JSON / a JSON array is rejected

`RigCommandsTest`: `camTopic("rvu/team11/labdip/data") == "rvu/team11/labdip/cam"`.

## Step 2: repository and MQTT routing

New `data/CameraRepository.kt`:
```kotlin
// shortcut: camera result kept in memory only, add a Room table if camera history is needed
class CameraRepository(private val clock: () -> Long = System::currentTimeMillis) {
    private val _latest = MutableStateFlow<CamShot?>(null)
    val latest: StateFlow<CamShot?> = _latest
    private val _dropped = MutableStateFlow(0)
    val dropped: StateFlow<Int> = _dropped

    fun ingest(bytes: ByteArray) = CamParser.parse(bytes).fold(
        onSuccess = { _latest.value = CamShot(it, clock()) },
        onFailure = { _dropped.update { n -> n + 1 } },
    )
}
```

`MqttRepository.kt`:
- add a flow next to `_messages`:
  ```kotlin
  private val _camera = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val camera: SharedFlow<ByteArray> = _camera
  ```
- in `connectTo`: `val cameraTopic = camTopic(b.topic)` (not `camTopic`, that name is the function)
- in the connected listener, subscribe like the others: `client?.subscribeWith()?.topicFilter(cameraTopic)?.qos(MqttQos.AT_LEAST_ONCE)?.send()`
- in the `publishes` `when`, before `else`: `cameraTopic -> _camera.tryEmit(it.payloadAsBytes)`

Without the `cameraTopic` branch, camera JSON would go to `_messages`, fail `ReadingParser` and count as "malformed messages" on Live. Check that this does not happen.

`AppContainer.kt`:
```kotlin
val camera = CameraRepository()
// in init:
scope.launch { mqtt.camera.collect { camera.ingest(it) } }
```

## Step 3: ViewModel

`LiveUiState`, new fields and helpers:
```kotlin
val cam: CamShot? = null,
...
val camAgeMs: Long? get() = cam?.let { now - it.receivedAt }
/** Same Pass/Marginal limits as the sensor, applied to the camera's ΔE2000. */
val camStatus: Status? get() = cam?.result?.deltaE2000?.let { statusFor(it, settings.passMax, settings.marginalMax) }
val camOtherSample: Boolean get() = cam != null && reading != null && cam.result.sampleId != reading.sampleId
```

`LiveViewModel`: `combine` takes at most 5 flows, which are all used. Put the camera in the existing nested pair:
```kotlin
combine(readings.dropped, ticker, camera.latest) { d, t, cam -> Triple(d, t, cam) },
) { (conn, rigOnline), reading, s, m, (dropped, now, cam) ->
    LiveUiState(conn, reading, s, m, dropped, now, rigOnline, cam)
}
```
Add `camera: CameraRepository` to the constructor and pass `c.camera` in `Factory`.

## Step 4: UI

Where: `LiveScreen`, directly under `SplitSwatch`, inside the same column. It sits outside the `stale` alpha, so a camera result is not dimmed just because the rig is quiet. Show it whenever `state.cam != null`, even when `reading == null`. In the `r == null` branch, add it under `EmptyHero`.

`CameraCard(state)` contents, top to bottom:
1. Title "Camera" (`LabType.cardTitle`) and on the right `ago(camAgeMs)`.
2. Warnings, each a line of `LabType.body` in `Marginal` with a `WarningAmber` icon:
   - `camOtherSample` → "Camera result is for sample S1, live reading is S2."
   - `camAgeMs > 10 min` → "Camera result is N min old."
   - `roi == "centre"` → "Patch not found, measured the image centre."
   - `white == "none"` → "No white reference: colour not corrected."
3. `SplitSwatch(measured = Color(R, G, B), target = target colour, deltaE = deltaE2000 ?: 0.0, status = camStatus)`. Target colour: `labToRgb(targetL, targetA, targetB)` from the camera result, else `state.target`. Without a target, show the measured swatch only plus "No target set".
   - `SplitSwatch` prints `"ΔE ${deltaE.fmt(2)}"` on the seam (`Swatch.kt` line 67) and "Delta E" in its content description. Add `label: String = "ΔE"` and pass `"ΔE2000"` (and "Delta E 2000" for the description) from the camera card.
4. Numbers row: `L a b` (reuse `LabRow`, make it `internal`), "ΔE2000 vs target" and "Camera vs sensor ΔE2000" (only when present).
5. `LevelnessGrid(result, tol = result.levelTol ?: 1.0)`.
6. A word under the grid: `uniform == true` → `StatusLabel(Status.Pass)` with the text "Uniform"; `false` → `Status.Fail` with "Uneven"; plus "max 2.57, mean 1.18 (limit 1.0)". `StatusLabel` prints Pass/Fail, so either add a `text` parameter or build the Row inline like `RangeWord`.

`LevelnessGrid` (new, `ui/components/LevelnessGrid.kt`):
```kotlin
@Composable
fun LevelnessGrid(r: CamResult, tol: Double, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SwatchSurround).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until r.grid) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (col in 0 until r.grid) {
                val cell = r.cells[row * r.grid + col]
                val fill = cell?.let { labToRgb(it[0], it[1], it[2]) }?.let { Color(it.first, it.second, it.third) } ?: InkFaint
                val st = cell?.let { statusFor(it[3], tol, 2 * tol) }
                Box(
                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(fill)
                        .border(3.dp, st?.color() ?: Color.Transparent, RoundedCornerShape(8.dp))
                        .semantics { contentDescription = cell?.let { "Cell ${row + 1},${col + 1}: ΔE ${it[3].fmt(2)}, ${st!!.word()}" } ?: "Cell ${row + 1},${col + 1}: no data" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(cell?.get(3)?.fmt(2) ?: "–", style = LabType.label,
                        color = if ((cell?.get(0) ?: 0.0) > 60) Color.Black else Color.White)
                }
            }
        }
    }
}
```
- The cell fill is the fabric's real measured colour on the neutral `SwatchSurround`, as `frontend.md` requires. The text flips black or white by L* so it stays readable on light and dark shades.
- The border colour repeats the status, but the status is never colour only: the cell text has the number, and the card has the Uniform/Uneven word plus icon.
- Keep the grid ≤ 280 dp wide on large phones (`widthIn(max = 280.dp)`, centred).

Previews (dark only): one with a 3×3 uneven result, one with a 2×2 uniform result without a target, one with `camOtherSample`.

## Step 5: test without the camera
From the repo root, with the app connected to the normal broker and topic:
```
python camera/cam_capture.py --file "WhatsApp Image 2026-11-05 at 19.50.57.jpeg" --mqtt --sample S1 --target 82 -3 15
```
This publishes a real result (uneven, max about 2.6) on `rvu/team11/labdip/cam`. Check:
- the card appears within a second; the swatch is pale yellow; the grid shows 9 cells, 1 red, several amber
- "malformed messages ignored" does **not** go up
- with the rig on, set the sample to S2 in Rig controls and publish again with `--sample S1`: the sample warning shows
- app in background and back: the card is still there; kill the app: the card is gone (in-memory shortcut)
- `--grid 2` and `--grid 4` render correctly
- release build: nothing changes, camera data is MQTT only

Then `./gradlew testDebugUnitTest lint` and report failures as-is.

## Step 6: docs
- `progress.md`: dated entry at the top.
- `architecture.md`: camera topic, `CameraRepository`, the in-memory exception.
- `explanation.md`: section on the camera card; topic list gains `cam`.
- `security.md`: `CamParser` rules (same as readings).
- `database.md`: no change (no new table).

## Demo flow (for reference)
1. Laptop: `python camera/cam_capture.py --host <CAM-IP> --led --flat images/WHITE_<stamp>.jpg --mqtt --loop`
2. App: set the sample ID and target in Rig controls (or CAPTURE on the reference swatch).
3. Sensor reading on the patch, then remove the cube, close the box, press Enter on the laptop.
4. The app shows the camera card: swatch, ΔE2000, levelness grid. Point at the uneven cell on the blotchy swatch.

## Known limits (say them if asked)
- Sensor ΔE is CIE76 (firmware), camera ΔE is CIEDE2000. The numbers are not on the same scale. The same Pass/Marginal limits are applied to both for simplicity.
- Camera vs sensor ΔE is an agreement check, not calibration: the two devices normalise colour differently.
- The camera result is not stored in the app (in memory only). The laptop keeps `camera_log.csv` and the photos.
