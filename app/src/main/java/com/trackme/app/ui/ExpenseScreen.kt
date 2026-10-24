@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.trackme.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.TrackViewModel
import com.trackme.app.data.ExpenseBook
import com.trackme.app.data.ExpenseEntry
import java.util.Calendar

@Composable
fun HotCount(text: String, hot: Boolean, color: Color, fontSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val pulse = if (hot) {
        val transition = rememberInfiniteTransition(label = "hot")
        val value by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
            label = "hotPulse",
        )
        value
    } else {
        1f
    }
    Text(
        text,
        color = if (hot) Color(0xFFFF1744).copy(alpha = pulse) else color,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.graphicsLayer {
            if (hot) scaleX = 0.96f + 0.06f * pulse
            if (hot) scaleY = 0.96f + 0.06f * pulse
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseScreen(vm: TrackViewModel) {
    val ctx = LocalContext.current
    val now = Calendar.getInstance()
    var year by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(now.get(Calendar.MONTH)) }
    var limitText by remember(vm.expenseLimit) {
        mutableStateOf(if (vm.expenseLimit <= 0.0) "" else trimMoney(vm.expenseLimit))
    }
    var title by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseBook.categories.first()) }
    var editingId by remember { mutableStateOf<String?>(null) }

    val monthItems = vm.expenses.filter { ExpenseBook.sameMonth(it.at, year, month) }
    val spent = monthItems.sumOf { it.price }
    val hot = vm.expenseLimit > 0.0 && spent >= vm.expenseLimit

    var pendingName by remember { mutableStateOf("expenses.csv") }
    var pendingBody by remember { mutableStateOf("") }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(pendingBody.toByteArray()) }
            }.isSuccess
            Toast.makeText(ctx, if (ok) "Saved $pendingName" else "Could not save that file", Toast.LENGTH_SHORT).show()
        }
    }
    fun download(name: String, items: List<ExpenseEntry>, heading: String) {
        pendingName = name
        pendingBody = ExpenseBook.csv(items, heading)
        saveCsv.launch(name)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen = com.trackme.app.Screen.HOME }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Expenses", fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(ExpenseBook.monthLabel(year, month), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val c = Calendar.getInstance()
                    c.set(Calendar.YEAR, year)
                    c.set(Calendar.MONTH, month)
                    c.add(Calendar.MONTH, -1)
                    year = c.get(Calendar.YEAR)
                    month = c.get(Calendar.MONTH)
                }) { Text("Previous") }
                TextButton(onClick = {
                    val c = Calendar.getInstance()
                    c.set(Calendar.YEAR, year)
                    c.set(Calendar.MONTH, month)
                    c.add(Calendar.MONTH, 1)
                    year = c.get(Calendar.YEAR)
                    month = c.get(Calendar.MONTH)
                }) { Text("Next") }
            }
            HotCount(
                text = ExpenseBook.money(spent),
                hot = hot,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 36.sp,
            )
            Text(
                if (vm.expenseLimit > 0.0) "of ${ExpenseBook.money(vm.expenseLimit)} this month" else "Set a monthly limit",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Monthly limit") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                )
                Button(onClick = { vm.updateExpenseLimit(limitText.toDoubleOrNull() ?: 0.0) }) { Text("Save") }
            }
            Text("Add a cost", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseBook.categories.forEach { option ->
                    FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option) })
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Details") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Price") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            Button(
                onClick = {
                    val price = priceText.toDoubleOrNull()
                    if (title.isBlank() || price == null || price <= 0.0) return@Button
                    val stamp = editingId?.let { id -> vm.expenses.firstOrNull { it.id == id }?.at } ?: monthStamp(year, month)
                    vm.saveExpense(editingId, title.trim(), category, price, stamp)
                    title = ""
                    priceText = ""
                    editingId = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (editingId == null) "Add" else "Update") }
            if (monthItems.isEmpty()) {
                Text("No costs in this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                monthItems.forEach { item ->
                    ExpenseRow(item, onEdit = {
                        editingId = item.id
                        title = item.title
                        category = item.category
                        priceText = trimMoney(item.price)
                    }, onDelete = { vm.deleteExpense(item.id) })
                }
            }
            Text("Download", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val (start, end) = ExpenseBook.weekBounds(System.currentTimeMillis())
                    val rows = vm.expenses.filter { it.at in start until end }
                    download("expenses-week.csv", rows, "This week")
                }) { Text("Week") }
                OutlinedButton(onClick = {
                    download("expenses-${year}-${month + 1}.csv", monthItems, ExpenseBook.monthLabel(year, month))
                }) { Text("Month") }
                OutlinedButton(onClick = {
                    val rows = vm.expenses.filter { ExpenseBook.yearOf(it.at) == year }
                    download("expenses-$year.csv", rows, year.toString())
                }) { Text("Year") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ExpenseRow(item: ExpenseEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.category, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(ExpenseBook.money(item.price), fontWeight = FontWeight.Bold)
        TextButton(onClick = onEdit) { Text("Edit") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
        }
    }
}

private fun trimMoney(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(java.util.Locale.US, "%.2f", value)

private fun monthStamp(year: Int, month: Int): Long {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance()
    c.set(Calendar.YEAR, year)
    c.set(Calendar.MONTH, month)
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 12)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    if (year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH)) return System.currentTimeMillis()
    return c.timeInMillis
}
