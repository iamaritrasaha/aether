package com.foresightlabs.aether.domain.text

/**
 * The styles a person can apply while writing.
 *
 * Deliberately only the ones a normal Telegram account can create. Link, mention,
 * hashtag and the rest are classified by the server from the text itself, so
 * offering them here would let Aether assert a classification it did not make.
 */
enum class ComposerStyle {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH,
    SPOILER,
    CODE
}

/**
 * Builds formatting entities from selections made in the composer.
 *
 * Everything here works in **UTF-16 code units**, matching TDLib exactly. Converting
 * to code points or to characters shifts every span in any message containing an
 * emoji, which is the single most common way formatted text arrives corrupted.
 *
 * The functions are pure so the same logic is exercised by tests and by the composer,
 * and so a selection never has to be reasoned about inside a composable.
 */
object ComposerFormatting {

    /**
     * Toggles [style] over `[start, end)`.
     *
     * Toggling is *symmetric*: if the whole selection already carries the style it is
     * removed, otherwise it is applied to the entire selection. Anything else makes a
     * partially-styled selection behave unpredictably — the common case of "select a
     * sentence, hit bold twice" has to end where it started.
     */
    fun toggle(
        entities: List<AetherEntity>,
        style: ComposerStyle,
        start: Int,
        end: Int
    ): List<AetherEntity> {
        if (end <= start) return entities
        return if (isFullyApplied(entities, style, start, end)) {
            remove(entities, style, start, end)
        } else {
            apply(entities, style, start, end)
        }
    }

    /** Whether every code unit in the range already carries [style]. */
    fun isFullyApplied(
        entities: List<AetherEntity>,
        style: ComposerStyle,
        start: Int,
        end: Int
    ): Boolean {
        if (end <= start) return false
        val covered = BooleanArray(end - start)
        entities.filter { styleOf(it) == style }.forEach { entity ->
            val from = maxOf(entity.offset, start)
            val to = minOf(entity.end, end)
            for (index in from until to) covered[index - start] = true
        }
        return covered.all { it }
    }

    /** The styles that apply to every code unit of the range, for toolbar state. */
    fun activeStyles(
        entities: List<AetherEntity>,
        start: Int,
        end: Int
    ): Set<ComposerStyle> =
        ComposerStyle.entries.filter { isFullyApplied(entities, it, start, end) }.toSet()

    private fun apply(
        entities: List<AetherEntity>,
        style: ComposerStyle,
        start: Int,
        end: Int
    ): List<AetherEntity> {
        val same = entities.filter { styleOf(it) == style }
        val others = entities.filter { styleOf(it) != style }
        // Merge with any run of the same style that touches this one, so repeated
        // application does not accumulate a pile of adjacent identical spans.
        var from = start
        var to = end
        same.forEach { entity ->
            if (entity.offset <= to && entity.end >= from) {
                from = minOf(from, entity.offset)
                to = maxOf(to, entity.end)
            }
        }
        val untouched = same.filter { it.end < from || it.offset > to }
        return (others + untouched + build(style, from, to - from)).sortedBy { it.offset }
    }

    private fun remove(
        entities: List<AetherEntity>,
        style: ComposerStyle,
        start: Int,
        end: Int
    ): List<AetherEntity> = entities.flatMap { entity ->
        if (styleOf(entity) != style || entity.end <= start || entity.offset >= end) {
            listOf(entity)
        } else {
            // Keep whatever fell outside the selection, on either side.
            buildList {
                if (entity.offset < start) add(build(style, entity.offset, start - entity.offset))
                if (entity.end > end) add(build(style, end, entity.end - end))
            }
        }
    }.sortedBy { it.offset }

    /**
     * Re-anchors entities after the text changed.
     *
     * Spans after the edit shift; spans spanning the removed range shrink; spans
     * entirely inside a removal disappear. Without this, editing the start of a
     * message drags every style out of position.
     *
     * @param changeStart where the replacement began, in UTF-16 code units
     * @param removed how many code units were removed
     * @param inserted how many were inserted
     */
    fun reanchor(
        entities: List<AetherEntity>,
        changeStart: Int,
        removed: Int,
        inserted: Int
    ): List<AetherEntity> {
        if (removed == 0 && inserted == 0) return entities
        val removalEnd = changeStart + removed
        val delta = inserted - removed
        return entities.mapNotNull { entity ->
            val style = styleOf(entity) ?: return@mapNotNull entity
            val from = shift(entity.offset, changeStart, removalEnd, delta, isStart = true)
            val to = shift(entity.end, changeStart, removalEnd, delta, isStart = false)
            if (to <= from) null else build(style, from, to - from)
        }
    }

    /**
     * Moves one boundary of a span across an edit.
     *
     * The two ends break the tie at an insertion point in opposite directions, which
     * together give the behaviour people expect: **text typed at either edge of a
     * styled run is not styled**. Typing immediately before bold text pushes the run
     * right; typing immediately after it leaves the run where it was.
     */
    private fun shift(
        position: Int,
        changeStart: Int,
        removalEnd: Int,
        delta: Int,
        isStart: Boolean
    ): Int = when {
        position < changeStart -> position
        position == changeStart -> if (isStart && removalEnd == changeStart) {
            // Pure insertion at this span's start: the new text goes before it.
            position + delta
        } else {
            position
        }
        position >= removalEnd -> position + delta
        // The boundary sat inside removed text; it collapses to the removal point.
        else -> changeStart
    }

    /** Trims spans to the text they describe and drops anything left empty. */
    fun sanitise(entities: List<AetherEntity>, textLength: Int): List<AetherEntity> =
        entities.mapNotNull { entity ->
            val style = styleOf(entity) ?: return@mapNotNull null
            val from = entity.offset.coerceIn(0, textLength)
            val to = entity.end.coerceIn(from, textLength)
            if (to <= from) null else build(style, from, to - from)
        }.sortedBy { it.offset }

    private fun styleOf(entity: AetherEntity): ComposerStyle? = when (entity) {
        is AetherEntity.Bold -> ComposerStyle.BOLD
        is AetherEntity.Italic -> ComposerStyle.ITALIC
        is AetherEntity.Underline -> ComposerStyle.UNDERLINE
        is AetherEntity.Strikethrough -> ComposerStyle.STRIKETHROUGH
        is AetherEntity.Spoiler -> ComposerStyle.SPOILER
        is AetherEntity.Code -> ComposerStyle.CODE
        else -> null
    }

    private fun build(style: ComposerStyle, offset: Int, length: Int): AetherEntity = when (style) {
        ComposerStyle.BOLD -> AetherEntity.Bold(offset, length)
        ComposerStyle.ITALIC -> AetherEntity.Italic(offset, length)
        ComposerStyle.UNDERLINE -> AetherEntity.Underline(offset, length)
        ComposerStyle.STRIKETHROUGH -> AetherEntity.Strikethrough(offset, length)
        ComposerStyle.SPOILER -> AetherEntity.Spoiler(offset, length)
        ComposerStyle.CODE -> AetherEntity.Code(offset, length)
    }
}
