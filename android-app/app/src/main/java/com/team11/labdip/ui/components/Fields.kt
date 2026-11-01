package com.team11.labdip.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.team11.labdip.ui.theme.Fail
import com.team11.labdip.ui.theme.GlassEdge
import com.team11.labdip.ui.theme.Ink
import com.team11.labdip.ui.theme.InkSoft
import com.team11.labdip.ui.theme.LabType

@Composable
fun labFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Ink,
    unfocusedBorderColor = GlassEdge.copy(alpha = 0.3f),
    focusedLabelColor = Ink,
    unfocusedLabelColor = InkSoft,
    cursorColor = Ink,
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    errorBorderColor = Fail,
    errorLabelColor = Fail,
    errorSupportingTextColor = Fail,
)

@Composable
fun LabField(
    label: String,
    value: String,
    error: String?,
    keyboard: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    signed: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        // Decimal keypads on some phones have no minus key, so signed fields use the full keyboard
        keyboardOptions = KeyboardOptions(keyboardType = if (signed) KeyboardType.Text else keyboard),
        shape = RoundedCornerShape(16.dp),
        textStyle = LabType.body,
        colors = labFieldColors(),
        modifier = modifier,
    )
}
