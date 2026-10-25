package com.arabicpagebuilder.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicpagebuilder.core.interaction.InteractionText
import com.arabicpagebuilder.core.interaction.InteractionValidator
import com.arabicpagebuilder.core.model.Action
import com.arabicpagebuilder.core.model.ActionType
import com.arabicpagebuilder.core.model.Condition
import com.arabicpagebuilder.core.model.ConditionType
import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.Interaction
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.Trigger
import com.arabicpagebuilder.core.model.TriggerType
import com.arabicpagebuilder.core.model.subtree
import kotlinx.coroutines.delay

/**
 * البرمجة المرئية: كل تفاعل ثلاث خطوات متصلة «عند ← إذا ← افعل» (مع «وإلا» عند وجود شروط).
 * لا يكتب المستخدم كودًا: يختار من قوائم ومنتقي عناصر، والمولّد يبني السكربت من هذه البيانات.
 */
@Composable
fun InteractionsTab(state: EditorState) {
    val doc = state.doc
    var expandedId by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text("التفاعلات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "اربط ثلاث خطوات بلا كتابة كود:  عند ← إذا ← افعل.  مثال: عند الضغط على زر ← (إذا كان الحقل فارغًا) ← أظهر قسم التفاصيل.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        val first = doc.root.subtree().firstOrNull { it.type == ElementType.BUTTON || it.type == ElementType.LINK }
                        val id = state.newInteractionId()
                        val rule = Interaction(id, "تفاعل ${doc.interactions.size + 1}", Trigger(TriggerType.CLICK, first?.id.orEmpty()))
                        state.apply { it.addInteraction(rule) }
                        expandedId = id
                    }) { Text("+ تفاعل جديد") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            val rule = InteractionText.templateToggleOnClick(doc, state.newInteractionId())
                            state.apply { it.addInteraction(rule) }
                            expandedId = rule.id
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("قالب: إظهار/إخفاء عند الضغط", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = {
                            val rule = InteractionText.templateValidateBeforeSubmit(doc, state.newInteractionId())
                            state.apply { it.addInteraction(rule) }
                            expandedId = rule.id
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("قالب: تحقق قبل الإرسال", fontSize = 12.sp) }
                }
                HorizontalDivider()
            }
        }
        if (doc.interactions.isEmpty()) {
            item {
                Text(
                    "لا توجد تفاعلات بعد. أضف عناصر في تبويب التصميم (زر وقسم مخفي «مخفي عند فتح الصفحة»)، ثم أنشئ تفاعلًا هنا.",
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        items(doc.interactions, key = { it.id }) { rule ->
            InteractionCard(
                doc = doc,
                rule = rule,
                expanded = expandedId == rule.id,
                onToggle = { expandedId = if (expandedId == rule.id) null else rule.id },
                onChange = { updated -> state.apply { it.updateInteraction(updated) } },
                onDelete = { state.apply { it.removeInteraction(rule.id) } },
            )
        }
        item { Box(Modifier.padding(bottom = 24.dp)) {} }
    }
}

@Composable
private fun InteractionCard(
    doc: PageDocument,
    rule: Interaction,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (Interaction) -> Unit,
    onDelete: () -> Unit,
) {
    val problems = remember(doc, rule) { InteractionValidator.check(doc, rule) }
    val (s1, s2, s3) = remember(doc, rule) { InteractionText.summary(doc, rule) }
    Column(
        Modifier.fillMaxWidth().border(1.dp, if (problems.isEmpty()) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rule.name.ifBlank { "تفاعل بلا اسم" }, fontWeight = FontWeight.Bold)
                Text(
                    if (problems.isEmpty()) "✓ جاهز" else "⚠ غير مكتمل (${problems.size})",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problems.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onToggle) { Text(if (expanded) "إغلاق" else "تعديل") }
            TextButton(onClick = onDelete) { Text("حذف") }
        }
        StepBox("1) عند", s1, MaterialTheme.colorScheme.primaryContainer)
        Arrow()
        StepBox("2) إذا", s2, MaterialTheme.colorScheme.secondaryContainer)
        Arrow()
        StepBox("3) افعل", s3, MaterialTheme.colorScheme.tertiaryContainer)
        for (p in problems) Text("• " + InteractionText.problemMessage(p), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (expanded) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            InteractionEditor(doc, rule, onChange)
        }
    }
}

@Composable
private fun StepBox(title: String, text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(text)
        }
    }
}

@Composable
private fun Arrow() {
    Text("⬇", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun InteractionEditor(doc: PageDocument, rule: Interaction, onChange: (Interaction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DebouncedField(rule.name, "اسم التفاعل") { onChange(rule.copy(name = it.take(InteractionValidator.MAX_NAME_LENGTH))) }

        // ---- 1) عند ----
        SectionTitle("1) عند…")
        ChoiceMenu(
            selectedLabel = InteractionText.triggerName(rule.trigger.type),
            options = TriggerType.entries.map { it to InteractionText.triggerName(it) },
        ) { type -> onChange(rule.copy(trigger = Trigger(type, ""))) }
        if (rule.trigger.type != TriggerType.PAGE_LOAD) {
            ElementPicker(doc, rule.trigger.target, InteractionText.triggerTargets(doc, rule.trigger.type)) {
                onChange(rule.copy(trigger = rule.trigger.copy(target = it)))
            }
        }

        // ---- 2) إذا ----
        SectionTitle("2) إذا… (اختياري)")
        if (rule.conditions.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rule.matchAll) Button(onClick = {}) { Text("كل الشروط") } else OutlinedButton(onClick = { onChange(rule.copy(matchAll = true)) }) { Text("كل الشروط") }
                if (!rule.matchAll) Button(onClick = {}) { Text("أي شرط") } else OutlinedButton(onClick = { onChange(rule.copy(matchAll = false)) }) { Text("أي شرط") }
            }
        }
        rule.conditions.forEachIndexed { index, cond ->
            key(index) {
                ConditionRow(
                    doc, cond,
                    onChange = { updated -> onChange(rule.copy(conditions = rule.conditions.toMutableList().also { it[index] = updated })) },
                    onRemove = { onChange(rule.copy(conditions = rule.conditions.filterIndexed { i, _ -> i != index })) },
                )
            }
        }
        if (rule.conditions.size < InteractionValidator.MAX_STEPS) {
            OutlinedButton(onClick = {
                val first = InteractionText.conditionTargets(doc, ConditionType.NOT_EMPTY).firstOrNull()?.id.orEmpty()
                onChange(rule.copy(conditions = rule.conditions + Condition(ConditionType.NOT_EMPTY, first)))
            }) { Text("+ شرط") }
        }

        // ---- 3) افعل ----
        SectionTitle("3) افعل…")
        ActionList(doc, rule.actions, "+ إجراء") { onChange(rule.copy(actions = it)) }

        if (rule.conditions.isNotEmpty()) {
            SectionTitle("وإلا (عند عدم تحقق الشروط)…")
            ActionList(doc, rule.elseActions, "+ إجراء بديل") { onChange(rule.copy(elseActions = it)) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("منع السلوك الافتراضي")
                Text("مثل فتح الرابط أو إرسال النموذج. عند الإيقاف يُرسَل النموذج إن تحققت الشروط، ويُمنَع إن لم تتحقق.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = rule.preventDefault, onCheckedChange = { onChange(rule.copy(preventDefault = it)) })
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun ConditionRow(doc: PageDocument, cond: Condition, onChange: (Condition) -> Unit, onRemove: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                ChoiceMenu(
                    selectedLabel = InteractionText.conditionName(cond.type),
                    options = ConditionType.entries.map { it to InteractionText.conditionName(it) },
                ) { type ->
                    val stillValid = InteractionText.conditionTargets(doc, type).any { it.id == cond.target }
                    onChange(cond.copy(type = type, target = if (stillValid) cond.target else ""))
                }
            }
            TextButton(onClick = onRemove) { Text("حذف") }
        }
        ElementPicker(doc, cond.target, InteractionText.conditionTargets(doc, cond.type)) { onChange(cond.copy(target = it)) }
        if (cond.type.needsValue) DebouncedField(cond.value, "القيمة") { onChange(cond.copy(value = it.take(InteractionValidator.MAX_VALUE_LENGTH))) }
    }
}

@Composable
private fun ActionList(doc: PageDocument, actions: List<Action>, addLabel: String, onChange: (List<Action>) -> Unit) {
    actions.forEachIndexed { index, action ->
        key(index) {
            ActionRow(
                doc, action,
                onChange = { updated -> onChange(actions.toMutableList().also { it[index] = updated }) },
                onRemove = { onChange(actions.filterIndexed { i, _ -> i != index }) },
            )
        }
    }
    if (actions.size < InteractionValidator.MAX_STEPS) {
        OutlinedButton(onClick = { onChange(actions + Action(ActionType.SHOW, "")) }) { Text(addLabel) }
    }
}

@Composable
private fun ActionRow(doc: PageDocument, action: Action, onChange: (Action) -> Unit, onRemove: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                ChoiceMenu(
                    selectedLabel = InteractionText.actionName(action.type),
                    options = ActionType.entries.map { it to InteractionText.actionName(it) },
                ) { type ->
                    val stillValid = type.needsTarget && InteractionText.actionTargets(doc, type).any { it.id == action.target }
                    onChange(action.copy(type = type, target = if (stillValid) action.target else ""))
                }
            }
            TextButton(onClick = onRemove) { Text("حذف") }
        }
        if (action.type.needsTarget) ElementPicker(doc, action.target, InteractionText.actionTargets(doc, action.type)) { onChange(action.copy(target = it)) }
        if (action.type.needsValue) {
            DebouncedField(action.value, if (action.type == ActionType.MESSAGE) "نص الرسالة" else "النص الجديد") {
                onChange(action.copy(value = it.take(InteractionValidator.MAX_VALUE_LENGTH)))
            }
        }
    }
}

@Composable
private fun <T> ChoiceMenu(selectedLabel: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$selectedLabel ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((value, label) in options) {
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    open = false
                    onSelect(value)
                })
            }
        }
    }
}

@Composable
private fun ElementPicker(doc: PageDocument, currentId: String, candidates: List<Element>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val mismatch = currentId.isNotBlank() && candidates.none { it.id == currentId }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (currentId.isBlank()) "اختر عنصرًا…" else InteractionText.elementName(doc, currentId) + if (mismatch) "  ⚠" else "",
            color = if (mismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("اختر عنصرًا") },
            text = {
                if (candidates.isEmpty()) {
                    Text("لا توجد عناصر مناسبة في الصفحة. أضف العنصر المطلوب من تبويب التصميم أولًا.")
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        for (e in candidates) {
                            TextButton(onClick = { open = false; onPick(e.id) }, modifier = Modifier.fillMaxWidth()) {
                                Text(InteractionText.elementName(doc, e.id), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text("إلغاء") } },
        )
    }
}

/** حقل نصي يطبّق تعديله بعد توقف الكتابة نصف ثانية (فلا يتضخم سجل التراجع بكل حرف). */
@Composable
private fun DebouncedField(value: String, label: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val latest by rememberUpdatedState(onCommit)
    LaunchedEffect(text) {
        if (text != value) {
            delay(500)
            latest(text)
        }
    }
    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}
