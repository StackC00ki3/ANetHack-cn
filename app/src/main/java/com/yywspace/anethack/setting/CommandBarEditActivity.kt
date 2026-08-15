package com.yywspace.anethack.setting

import android.content.ClipData
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.DragEvent
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.yywspace.anethack.R
import com.yywspace.anethack.SharedPreferencesUtils
import com.yywspace.anethack.databinding.ActivityCommandBarEditBinding
import com.yywspace.anethack.keybord.TouchAction
import com.yywspace.anethack.keybord.TouchActionCatalog
import com.yywspace.anethack.keybord.TouchActionIcons
import com.yywspace.anethack.keybord.TouchCommandBar
import com.yywspace.anethack.keybord.TouchCommandRow


/**
 * Visual editor for the customizable bottom command bar: adjust the row count,
 * add/remove buttons and change each button's command, label and icon.
 * Every change is persisted immediately to SharedPreferencesUtils.commandBar.
 */
class CommandBarEditActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCommandBarEditBinding
    private lateinit var prefs: SharedPreferencesUtils
    private var rows: List<TouchCommandRow> = emptyList()
    private var dropIndicator: View? = null
    private var draggedChip: View? = null

    private companion object {
        const val ADD_TILE_TAG = "add_tile"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommandBarEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = SharedPreferencesUtils(this)
        setSupportActionBar(binding.commandBarEditToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        initStatusBar()
        rows = TouchCommandBar.parseRows(prefs.commandBar)
        binding.commandBarAddRow.setOnClickListener {
            if (!TouchCommandBar.canAddRow(rows)) {
                toast(getString(R.string.cmd_bar_max_rows, TouchCommandBar.MAX_ROWS))
                return@setOnClickListener
            }
            mutate(TouchCommandBar::addRow)
        }
        binding.commandBarReset.setOnClickListener { confirmReset() }
        initDragAndDrop()
        renderRows()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initStatusBar() {
        window.statusBarColor = Color.TRANSPARENT
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun mutate(transform: (List<TouchCommandRow>) -> List<TouchCommandRow>) {
        rows = transform(rows)
        prefs.commandBar = TouchCommandBar.serialize(rows)
        renderRows()
    }

    private fun renderRows() {
        binding.commandBarRows.removeAllViews()
        // Row numbers count from the bottom of the screen; the editor simply
        // lists them top-to-bottom (row 1 first).
        rows.forEachIndexed { rowIndex, row ->
            binding.commandBarRows.addView(buildRowView(rowIndex, row))
        }
        binding.commandBarAddRow.isEnabled = TouchCommandBar.canAddRow(rows)
    }

    private fun buildRowView(rowIndex: Int, row: TouchCommandRow): View {
        val rowView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                text = getString(R.string.cmd_bar_row_title, rowIndex + 1)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(TextView(this).apply {
            text = getString(R.string.cmd_bar_visible_limit)
            textSize = 12f
            setTextColor(Color.GRAY)
        })
        header.addView(buildLimitStepper(rowIndex, row, -1))
        header.addView(TextView(this).apply {
            text = row.visibleLimit.toString()
            textSize = 14f
            minWidth = dp(24)
            gravity = Gravity.CENTER
        })
        header.addView(buildLimitStepper(rowIndex, row, +1))
        header.addView(TextView(this).apply {
            text = getString(
                R.string.cmd_bar_row_count,
                row.actions.size,
                TouchCommandBar.MAX_BUTTONS_PER_ROW,
            )
            textSize = 12f
            setTextColor(Color.GRAY)
            marginEnd(dp(8))
        })
        header.addView(TextView(this).apply {
            text = getString(R.string.cmd_bar_delete_row)
            textSize = 13f
            setTextColor(getColor(R.color.touch_bar_edit_accent))
            setPadding(dp(8), dp(4), dp(4), dp(4))
            setOnClickListener { confirmDeleteRow(rowIndex) }
        })
        rowView.addView(header)

        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.actions.forEachIndexed { buttonIndex, action ->
            chips.addView(buildChip(action).apply {
                setOnClickListener { showButtonEditor(rowIndex, buttonIndex, action) }
                setOnLongClickListener { view -> startButtonDrag(view, rowIndex, buttonIndex) }
            })
        }
        if (TouchCommandBar.canAddButton(rows, rowIndex)) {
            chips.addView(buildAddTile().apply {
                tag = ADD_TILE_TAG
                setOnClickListener { showButtonEditor(rowIndex, -1, null) }
            })
        }
        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        chipScroll.setOnDragListener { _, event ->
            onRowDragEvent(event, rowIndex, chips, chipScroll.scrollX)
        }
        rowView.addView(chipScroll)
        rowView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(12) }
        return rowView
    }

    private fun buildLimitStepper(rowIndex: Int, row: TouchCommandRow, delta: Int): View {
        return TextView(this).apply {
            text = if (delta < 0) "−" else "＋"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.touch_bar_edit_accent))
            setPadding(dp(6), 0, dp(6), 0)
            isEnabled = if (delta < 0) row.visibleLimit > 1
            else row.visibleLimit < TouchCommandBar.MAX_VISIBLE_LIMIT
            alpha = if (isEnabled) 1f else 0.3f
            setOnClickListener {
                mutate { TouchCommandBar.setRowLimit(it, rowIndex, row.visibleLimit + delta) }
            }
        }
    }

    private fun buildChip(action: TouchAction): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumWidth = dp(60)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setBackgroundResource(R.drawable.touch_bar_edit_chip)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(8) }
            addView(ImageView(context).apply {
                setImageResource(action.iconRes)
                setColorFilter(Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            })
            addView(TextView(context).apply {
                text = action.label(context)
                textSize = 10f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                maxWidth = dp(72)
            })
        }
    }

    private fun buildAddTile(): View {
        return TextView(this).apply {
            text = "+"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.touch_bar_edit_accent))
            setBackgroundResource(R.drawable.touch_bar_edit_chip)
            contentDescription = getString(R.string.cmd_bar_add_button)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(52)).apply {
                marginEnd = dp(8)
            }
        }
    }

    private fun showButtonEditor(rowIndex: Int, buttonIndex: Int, existing: TouchAction?) {
        var selectedIconKey = existing?.let { TouchActionIcons.keyForRes(it.iconRes) }
            ?: TouchActionIcons.keyForRes(R.drawable.ic_touch_fallback)
        var iconTouched = false

        val fieldMargin = dp(16)
        val commandInput = AutoCompleteTextView(this).apply {
            hint = getString(R.string.cmd_bar_command)
            setText(existing?.command.orEmpty())
            threshold = 1
            val suggestions = (TouchActionCatalog.builtInCategories()
                .flatMap { it.pages.flatten() }
                .map { it.command } + listOf("27", "i", "Keyboard", "Center", "Setting"))
                .distinct()
            setAdapter(ArrayAdapter(this@CommandBarEditActivity, android.R.layout.simple_list_item_1, suggestions))
            setOnItemClickListener { _, _, _, _ ->
                // Adopting a known command also adopts its default icon unless the
                // user picked one manually.
                if (!iconTouched) {
                    selectedIconKey = TouchActionIcons.keyForRes(
                        TouchActionIcons.forCommand(text.toString().trim()),
                    )
                }
            }
        }
        val labelInput = EditText(this).apply {
            hint = getString(R.string.cmd_bar_label_hint)
            setText(existing?.customLabel.orEmpty())
        }
        val iconAdapter = IconGridAdapter { selectedIconKey }
        val iconGrid = GridView(this).apply {
            numColumns = 5
            verticalSpacing = dp(4)
            horizontalSpacing = dp(4)
            adapter = iconAdapter
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(220),
            )
            setOnItemClickListener { _, _, position, _ ->
                selectedIconKey = TouchActionIcons.selectableIcons[position].first
                iconTouched = true
                iconAdapter.notifyDataSetChanged()
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(fieldMargin, dp(8), fieldMargin, 0)
            addView(fieldLabel(getString(R.string.cmd_bar_command)))
            addView(commandInput)
            addView(fieldLabel(getString(R.string.cmd_bar_label)))
            addView(labelInput)
            addView(fieldLabel(getString(R.string.cmd_bar_icon)))
            addView(iconGrid)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.cmd_bar_add_button else R.string.cmd_bar_edit_button)
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton(R.string.dialog_confirm, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .apply {
                if (existing != null) {
                    setNeutralButton(R.string.cmd_bar_delete) { _, _ ->
                        mutate { TouchCommandBar.removeButton(it, rowIndex, buttonIndex) }
                    }
                }
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val command = TouchCommandBar.sanitizeToken(commandInput.text.toString())
                if (command.isEmpty()) {
                    toast(getString(R.string.cmd_bar_command_required))
                    return@setOnClickListener
                }
                val base = TouchCommandBar.newAction(command)
                val label = TouchCommandBar.sanitizeToken(labelInput.text.toString())
                    .takeUnless { it.isEmpty() }
                val action = base.copy(
                    labelRes = if (label == null) base.labelRes else 0,
                    customLabel = label,
                    iconRes = selectedIconKey?.let(TouchActionIcons::resForKey) ?: base.iconRes,
                )
                mutate {
                    if (existing == null) TouchCommandBar.addButton(it, rowIndex, action)
                    else TouchCommandBar.updateButton(it, rowIndex, buttonIndex, action)
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun confirmDeleteRow(rowIndex: Int) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.cmd_bar_delete_row_confirm, rowIndex + 1))
            .setPositiveButton(R.string.cmd_bar_delete) { _, _ ->
                mutate { TouchCommandBar.removeRow(it, rowIndex) }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setMessage(R.string.cmd_bar_reset_confirm)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                mutate { TouchCommandBar.parseRows(TouchCommandBar.DEFAULT_CONFIG) }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun initDragAndDrop() {
        // Long-press a button chip to drag it: drop on another position to
        // reorder or move across rows, drop on the bottom zone to delete.
        binding.root.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    binding.commandBarDeleteZone.visibility = View.VISIBLE
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    binding.commandBarDeleteZone.visibility = View.GONE
                    clearDropIndicator()
                    draggedChip?.alpha = 1f
                    draggedChip = null
                    true
                }
                else -> true
            }
        }
        binding.commandBarDeleteZone.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.alpha = 0.7f
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    view.alpha = 1f
                    true
                }
                DragEvent.ACTION_DROP -> {
                    view.alpha = 1f
                    val from = event.localState as? IntArray
                    if (from != null) {
                        mutate { TouchCommandBar.removeButton(it, from[0], from[1]) }
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun startButtonDrag(view: View, rowIndex: Int, buttonIndex: Int): Boolean {
        draggedChip = view
        val clip = ClipData.newPlainText("commandBarButton", "$rowIndex:$buttonIndex")
        view.startDragAndDrop(
            clip,
            View.DragShadowBuilder(view),
            intArrayOf(rowIndex, buttonIndex),
            0,
        )
        view.post { view.alpha = 0.4f }
        return true
    }

    private fun onRowDragEvent(
        event: DragEvent,
        rowIndex: Int,
        chips: LinearLayout,
        scrollX: Int,
    ): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_LOCATION ->
                showDropIndicator(chips, insertionIndex(chips, event.x + scrollX))
            DragEvent.ACTION_DRAG_EXITED -> clearDropIndicator()
            DragEvent.ACTION_DROP -> {
                val from = event.localState as? IntArray ?: return false
                val toIndex = insertionIndex(chips, event.x + scrollX)
                clearDropIndicator()
                mutate {
                    TouchCommandBar.moveButton(it, from[0], from[1], rowIndex, toIndex)
                }
            }
            DragEvent.ACTION_DRAG_ENDED -> clearDropIndicator()
        }
        return true
    }

    /** Index among the row's buttons (add tile excluded) matching a chips-local x. */
    private fun insertionIndex(chips: LinearLayout, x: Float): Int {
        var index = 0
        for (i in 0 until chips.childCount) {
            val child = chips.getChildAt(i)
            if (child === dropIndicator || child.tag == ADD_TILE_TAG) continue
            if (child.left + child.width / 2f < x) index++
        }
        return index
    }

    private fun showDropIndicator(chips: LinearLayout, index: Int) {
        val indicator = dropIndicator ?: View(this).apply {
            setBackgroundColor(getColor(R.color.touch_bar_edit_accent))
            dropIndicator = this
        }
        (indicator.parent as? LinearLayout)?.removeView(indicator)
        // With the indicator detached, chip i sits at array position i; never
        // insert past the add tile.
        val addTilePos = (0 until chips.childCount)
            .firstOrNull { chips.getChildAt(it).tag == ADD_TILE_TAG }
            ?: chips.childCount
        chips.addView(
            indicator,
            index.coerceIn(0, addTilePos),
            LinearLayout.LayoutParams(dp(3), dp(52)).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            },
        )
    }

    private fun clearDropIndicator() {
        (dropIndicator?.parent as? LinearLayout)?.removeView(dropIndicator)
    }

    private fun fieldLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.GRAY)
        setPadding(0, dp(8), 0, 0)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun TextView.marginEnd(value: Int) {
        val params = layoutParams as? LinearLayout.LayoutParams
        if (params != null) {
            params.marginEnd = value
        } else {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = value }
        }
    }

    private inner class IconGridAdapter(
        private val selectedKey: () -> String?,
    ) : BaseAdapter() {
        override fun getCount(): Int = TouchActionIcons.selectableIcons.size

        override fun getItem(position: Int): Pair<String, Int> = TouchActionIcons.selectableIcons[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val (key, resId) = getItem(position)
            val imageView = (convertView as? ImageView) ?: ImageView(this@CommandBarEditActivity).apply {
                layoutParams = ViewGroup.LayoutParams(dp(48), dp(48))
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setBackgroundResource(R.drawable.touch_bar_edit_icon_background)
            }
            imageView.setImageResource(resId)
            imageView.setColorFilter(Color.BLACK)
            imageView.isSelected = key == selectedKey()
            return imageView
        }
    }
}
