package com.machandler.haptichabits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.machandler.haptichabits.data.HabitButton
import com.machandler.haptichabits.ui.theme.HapticHabitsTheme

/** Stands in for a button id while the "New button" dialog is open (real ids start at 1). */
private const val NEW_BUTTON_ID = 0L

/**
 * Settings -> Buttons: lists the active buttons. Tapping a row edits its name and value, the
 * trash icon deletes it (after a confirmation), and "Add button" adds one at the end.
 *
 * [buttons] is null while loading. [onAdd] and [onUpdate] take the raw text from the dialog and
 * return false if it isn't valid (the dialog then stays open).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonsScreen(
    buttons: List<HabitButton>?,
    onAdd: (name: String, value: String) -> Boolean,
    onUpdate: (id: Long, name: String, value: String) -> Boolean,
    onDelete: (id: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which dialog is open, by button id; saveable so rotating the phone keeps it open.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Buttons") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editingId = NEW_BUTTON_ID }) {
                Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Add button")
            }
        },
    ) { innerPadding ->
        when {
            buttons == null -> Unit
            buttons.isEmpty() -> Text(
                text = "No buttons. Add one to show it on the home screen.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                // Lets the last row scroll clear of the Add button.
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                items(buttons, key = { it.id }) { button ->
                    ListItem(
                        headlineContent = { Text(button.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(ButtonRules.formatValue(button.weight)) },
                        trailingContent = {
                            IconButton(onClick = { deletingId = button.id }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete),
                                    contentDescription = "Delete ${button.name}",
                                )
                            }
                        },
                        modifier = Modifier.clickable { editingId = button.id },
                    )
                }
            }
        }
    }

    val editing = editingId
    if (editing == NEW_BUTTON_ID) {
        ButtonEditorDialog(
            title = "New button",
            initialName = "",
            initialValue = "1",
            onSave = { name, value -> if (onAdd(name, value)) editingId = null },
            onDismiss = { editingId = null },
        )
    } else if (editing != null) {
        buttons?.find { it.id == editing }?.let { button ->
            ButtonEditorDialog(
                title = "Edit button",
                initialName = button.name,
                initialValue = button.weight.toString(),
                onSave = { name, value -> if (onUpdate(button.id, name, value)) editingId = null },
                onDismiss = { editingId = null },
            )
        }
    }

    deletingId?.let { id ->
        buttons?.find { it.id == id }?.let { button ->
            AlertDialog(
                onDismissRequest = { deletingId = null },
                title = { Text("Delete button?") },
                text = {
                    Text("“${button.name}” will be removed. Presses already recorded stay in the total.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        onDelete(button.id)
                        deletingId = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { deletingId = null }) { Text("Cancel") }
                },
            )
        }
    }
}

/**
 * Asks for a button's name and value. Save is enabled only when both are valid (see [ButtonRules]).
 * The value field accepts a leading "-", and the "+/-" button flips the sign for keyboards
 * whose number pad has no minus key.
 */
@Composable
private fun ButtonEditorDialog(
    title: String,
    initialName: String,
    initialValue: String,
    onSave: (name: String, value: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var value by rememberSaveable { mutableStateOf(initialValue) }
    val valid = ButtonRules.cleanName(name) != null && ButtonRules.parseValue(value) != null
    val valueError = ButtonRules.valueError(value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(ButtonRules.MAX_NAME_LENGTH) },
                    label = { Text("Name") },
                    singleLine = true,
                    supportingText = { Text("${name.length} / ${ButtonRules.MAX_NAME_LENGTH}") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (ButtonRules.isTypeableValue(it)) value = it },
                    label = { Text("Value") },
                    singleLine = true,
                    isError = valueError != null,
                    supportingText = { Text(valueError ?: "Use a negative value for good habits") },
                    trailingIcon = {
                        TextButton(onClick = { value = ButtonRules.toggleSign(value) }) { Text("+/−") }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (valid) onSave(name, value) }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, value) }, enabled = valid) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Preview(showBackground = true)
@Composable
fun ButtonsScreenPreview() {
    HapticHabitsTheme {
        ButtonsScreen(
            buttons = listOf(
                HabitButton(id = 1, name = "+1", weight = 1, position = 0),
                HabitButton(id = 2, name = "Doomscrolling on the phone in bed at night", weight = 5, position = 1),
                HabitButton(id = 3, name = "Went for a walk", weight = -2, position = 2),
            ),
            onAdd = { _, _ -> true },
            onUpdate = { _, _, _ -> true },
            onDelete = {},
            onBack = {},
        )
    }
}
