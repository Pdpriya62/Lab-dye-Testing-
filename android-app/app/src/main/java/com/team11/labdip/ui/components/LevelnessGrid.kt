package com.team11.labdip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.team11.labdip.data.model.CamResult
import com.team11.labdip.data.model.labToRgb
import com.team11.labdip.data.model.statusFor
import com.team11.labdip.ui.theme.InkFaint
import com.team11.labdip.ui.theme.LabType
import com.team11.labdip.ui.theme.SwatchSurround

/**
 * Camera levelness map: each cell in its own measured colour on the neutral surround, its ΔE2000 against the
 * patch average inside, border = status (≤ tol pass, ≤ 2 × tol marginal, above fail). Number + word elsewhere,
 * so status never relies on the border colour alone.
 */
@Composable
fun LevelnessGrid(r: CamResult, tol: Double, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier.widthIn(max = 280.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SwatchSurround).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (row in 0 until r.grid) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until r.grid) {
                    val cell = r.cells.getOrNull(row * r.grid + col)
                    val fill = cell?.let { labToRgb(it[0], it[1], it[2]) }?.let { Color(it.first, it.second, it.third) } ?: InkFaint
                    val st = cell?.let { statusFor(it[3], tol, 2 * tol) }
                    val where = "Cell row ${row + 1}, column ${col + 1}"
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(shape)
                            .background(fill)
                            .border(3.dp, st?.color() ?: Color.Transparent, shape)
                            .semantics {
                                contentDescription = if (cell == null || st == null) "$where: no data"
                                else "$where: delta E 2000 ${cell[3].fmt(2)}, ${st.word().lowercase()}"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            cell?.get(3)?.fmt(2) ?: "–",
                            style = LabType.label,
                            color = if ((cell?.get(0) ?: 0.0) > 60) Color.Black else Color.White,
                        )
                    }
                }
            }
        }
    }
}
