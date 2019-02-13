/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("NOTHING_TO_INLINE")

package androidx.ui.core

import androidx.ui.lerpFloat
import kotlin.math.sqrt

/**
 * Dimension value representing device-independent pixels (dp). Component APIs specify their
 * dimensions such as line thickness in DP with Dimension objects. Hairline (1 pixel) thickness
 * may be specified with [Hairline], a dimension that take up no space. Dimensions are normally
 * defined using [dp], which can be applied to [Int], [Double], and [Float].
 *     val leftMargin = 10.dp
 *     val rightMargin = 10f.dp
 *     val topMargin = 20.0.dp
 *     val bottomMargin = 10.dp
 * Drawing is done in pixels. To retrieve the pixel size of a Dimension, use [toPx]:
 *     val lineThicknessPx = lineThickness.toPx(context)
 * [toPx] is normally needed only for painting operations.
 */
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
data /*inline*/ class Dimension(val dp: Float)

/**
 * A dimension used to represent a hairline drawing element. Hairline elements take
 * up no space, but will draw a single pixel, independent of the device's resolution and density.
 */
val Hairline = Dimension(dp = 0f)

// TODO(mount): regain the inline in the below extension properties. These don't work with jococo
/**
 * Create a [Dimension] using an [Int]:
 *     val left = 10
 *     val x = left.dp
 *     // -- or --
 *     val y = 10.dp
 */
/*inline*/ val Int.dp: Dimension get() = Dimension(dp = this.toFloat())

/**
 * Create a [Dimension] using a [Double]:
 *     val left = 10.0
 *     val x = left.dp
 *     // -- or --
 *     val y = 10.0.dp
 */
/*inline*/ val Double.dp: Dimension get() = Dimension(dp = this.toFloat())

/**
 * Create a [Dimension] using a [Float]:
 *     val left = 10f
 *     val x = left.dp
 *     // -- or --
 *     val y = 10f.dp
 */
/*inline*/ val Float.dp: Dimension get() = Dimension(dp = this)

/*inline*/ operator fun Float.div(by: Dimension) =
    DimensionInverse(this / by.dp)

/*inline*/ operator fun Double.div(by: Dimension) =
    DimensionInverse(this.toFloat() / by.dp)

/*inline*/ operator fun Int.div(by: Dimension) =
    DimensionInverse(this / by.dp)

/*inline*/ operator fun Float.times(by: Dimension) =
    Dimension(this * by.dp)

/*inline*/ operator fun Double.times(by: Dimension) =
    Dimension(this.toFloat() * by.dp)

/*inline*/ operator fun Int.times(by: Dimension) =
    Dimension(this * by.dp)

/**
 * Add two Dimensions together.
 */
/*inline*/ operator fun Dimension.plus(dimension: Dimension) =
    Dimension(dp = dp + dimension.dp)

/**
 * Subtract a Dimension from another one.
 */
/*inline*/ operator fun Dimension.minus(dimension: Dimension) =
    Dimension(dp = dp - dimension.dp)

/**
 * This is the same as multiplying the Dimension by -1.0.
 */
/*inline*/ operator fun Dimension.unaryMinus() = Dimension(-dp)

/**
 * Divide a Dimension by a scalar.
 */
/*inline*/ operator fun Dimension.div(by: Float): Dimension =
    Dimension(dp = dp / by)

/*inline*/ operator fun Dimension.div(by: Int): Dimension =
    Dimension(dp = dp / by)

/**
 * Divide by another Dimension to get a scalar.
 */
/*inline*/ operator fun Dimension.div(by: Dimension): Float = dp / by.dp

/**
 * Divide by [DimensionSquared] to get a [DimensionInverse].
 */
/*inline*/ operator fun Dimension.div(by: DimensionSquared): DimensionInverse =
    DimensionInverse(idp = dp / by.dp2)

/**
 * Multiply a Dimension by a scalar.
 */
/*inline*/ operator fun Dimension.times(by: Float): Dimension =
    Dimension(dp = dp * by)

/*inline*/ operator fun Dimension.times(by: Int): Dimension =
    Dimension(dp = dp * by)

/**
 * Multiply by a Dimension to get a [DimensionSquared] result.
 */
/*inline*/ operator fun Dimension.times(by: Dimension): DimensionSquared =
    DimensionSquared(dp2 = dp * by.dp)

/**
 * Multiply by a Dimension to get a [DimensionSquared] result.
 */
/*inline*/ operator fun Dimension.times(by: DimensionSquared): DimensionCubed =
    DimensionCubed(dp3 = dp * by.dp2)

/**
 * Support comparing Dimensions with comparison operators.
 */
/*inline*/ operator fun Dimension.compareTo(other: Dimension) = dp.compareTo(other.dp)

/*inline*/ fun min(dimension1: Dimension, dimension2: Dimension): Dimension {
    return if (dimension1 < dimension2) {
        dimension1
    } else {
        dimension2
    }
}

/*inline*/ fun max(dimension1: Dimension, dimension2: Dimension): Dimension {
    return if (dimension1 > dimension2) {
        dimension1
    } else {
        dimension2
    }
}

/**
 * Ensures that this value lies in the specified range [minimumValue]..[maximumValue].
 *
 * @return this value if it's in the range, or [minimumValue] if this value is less than
 * [minimumValue], or [maximumValue] if this value is greater than [maximumValue].
 */
fun Dimension.coerceIn(minimumValue: Dimension, maximumValue: Dimension): Dimension {
    if (minimumValue > maximumValue) {
        throw IllegalArgumentException(
            "Cannot coerce value to an empty range: maximum " +
                    "$maximumValue is less than minimum $minimumValue."
        )
    }
    return when {
        this < minimumValue -> minimumValue
        this > maximumValue -> maximumValue
        else -> this
    }
}

/**
 * Ensures that this value is not less than the specified [minimumValue].
 *
 * @return this value if it's greater than or equal to the [minimumValue] or the
 * [minimumValue] otherwise.
 */
fun Dimension.coerceAtLeast(minimumValue: Dimension): Dimension {
    return if (this < minimumValue) minimumValue else this
}

/**
 * Ensures that this value is not greater than the specified [maximumValue].
 *
 * @return this value if it's less than or equal to the [maximumValue] or the
 * [maximumValue] otherwise.
 */
fun Dimension.coerceAtMost(maximumValue: Dimension): Dimension {
    return if (this > maximumValue) maximumValue else this
}

/**
 * Linearly interpolate between two [Dimension]s.
 *
 * The `t` argument represents position on the timeline, with 0.0 meaning
 * that the interpolation has not started, returning `a` (or something
 * equivalent to `a`), 1.0 meaning that the interpolation has finished,
 * returning `b` (or something equivalent to `b`), and values in between
 * meaning that the interpolation is at the relevant point on the timeline
 * between `a` and `b`. The interpolation can be extrapolated beyond 0.0 and
 * 1.0, so negative values and values greater than 1.0 are valid.
 */
fun lerp(a: Dimension, b: Dimension, t: Float): Dimension {
    return Dimension(lerpFloat(a.dp, b.dp, t))
}

/**
 * A two dimensional size using [Dimension] for units
 */
data class Size(val width: Dimension, val height: Dimension)

/**
 * Returns the [Position] of the center of the rect from the point of [0, 0]
 * with this [Size].
 */
fun Size.center(): Position {
    return Position(width / 2f, height / 2f)
}

/**
 * A two-dimensional position using [Dimension] for units
 */
data class Position(val x: Dimension, val y: Dimension)

/**
 * The magnitude of the offset represented by this [Position].
 */
fun Position.getDistance(): Dimension {
    return Dimension(sqrt(x.dp * x.dp + y.dp * y.dp))
}

/**
 * Linearly interpolate between two [Position]s.
 *
 * The `t` argument represents position on the timeline, with 0.0 meaning
 * that the interpolation has not started, returning `a` (or something
 * equivalent to `a`), 1.0 meaning that the interpolation has finished,
 * returning `b` (or something equivalent to `b`), and values in between
 * meaning that the interpolation is at the relevant point on the timeline
 * between `a` and `b`. The interpolation can be extrapolated beyond 0.0 and
 * 1.0, so negative values and values greater than 1.0 are valid.
 */
fun lerp(a: Position, b: Position, t: Float): Position =
    Position(lerp(a.x, b.x, t), lerp(a.y, b.y, t))

/**
 * Subtract a [Position] from another one.
 */
/*inline*/ operator fun Position.minus(other: Position) =
    Position(x - other.x, y - other.y)

/**
 * Subtract a [Dimension] from the [Position]
 */
/*inline*/ operator fun Position.minus(dimension: Dimension) =
    Position(x - dimension, y - dimension)

/**
 * Holds a unit of squared dimensions, such as `1.dp * 2.dp`. [DimensionSquared], [DimensionCubed],
 * and [DimensionInverse] are used primarily for [Dimension] calculations to ensure resulting
 * units are as expected. Many times, [Dimension] calculations use scalars to determine the final
 * dimension during calculation:
 *     val width = oldWidth * stretchAmount
 * Other times, it is useful to do intermediate calculations with Dimensions directly:
 *     val width = oldWidth * newTotalWidth / oldTotalWidth
 */
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class DimensionSquared(val dp2: Float)

/**
 * Add two DimensionSquares together.
 */
/*inline*/ operator fun DimensionSquared.plus(dimension: DimensionSquared) =
    DimensionSquared(dp2 = dp2 + dimension.dp2)

/**
 * Subtract a DimensionSquare from another one.
 */
/*inline*/ operator fun DimensionSquared.minus(dimension: DimensionSquared) =
    DimensionSquared(dp2 = dp2 - dimension.dp2)

/**
 * Divide a DimensionSquare by a scalar.
 */
/*inline*/ operator fun DimensionSquared.div(by: Float): DimensionSquared =
    DimensionSquared(dp2 = dp2 / by)

/**
 * Divide by a [Dimension] to get a [Dimension] result.
 */
/*inline*/ operator fun DimensionSquared.div(by: Dimension): Dimension =
    Dimension(dp = dp2 / by.dp)

/**
 * Divide by a DimensionSquared to get a scalar result.
 */
/*inline*/ operator fun DimensionSquared.div(by: DimensionSquared): Float = dp2 / by.dp2

/**
 * Divide by a [DimensionCubed] to get a [DimensionInverse] result.
 */
/*inline*/ operator fun DimensionSquared.div(by: DimensionCubed): DimensionInverse =
    DimensionInverse(dp2 / by.dp3)

/**
 * Multiply by a scalar to get a DimensionSquared result.
 */
/*inline*/ operator fun DimensionSquared.times(by: Float): DimensionSquared =
    DimensionSquared(dp2 = dp2 * by)

/**
 * Multiply by a scalar to get a DimensionSquared result.
 */
/*inline*/ operator fun DimensionSquared.times(by: Dimension): DimensionCubed =
    DimensionCubed(dp3 = dp2 * by.dp)

/**
 * Support comparing DimensionSquared with comparison operators.
 */
/*inline*/ operator fun DimensionSquared.compareTo(other: DimensionSquared) =
    dp2.compareTo(other.dp2)

/**
 * Holds a unit of cubed dimensions, such as `1.dp * 2.dp * 3.dp`. [DimensionSquared],
 * [DimensionCubed], and [DimensionInverse] are used primarily for [Dimension] calculations to
 * ensure resulting units are as expected. Many times, [Dimension] calculations use scalars to
 * determine the final dimension during calculation:
 *     val width = oldWidth * stretchAmount
 * Other times, it is useful to do intermediate calculations with Dimensions directly:
 *     val width = oldWidth * newTotalWidth / oldTotalWidth
 */
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class DimensionCubed(val dp3: Float)

/**
 * Add two DimensionCubed together.
 */
/*inline*/ operator fun DimensionCubed.plus(dimension: DimensionCubed) =
    DimensionCubed(dp3 = dp3 + dimension.dp3)

/**
 * Subtract a DimensionCubed from another one.
 */
/*inline*/ operator fun DimensionCubed.minus(dimension: DimensionCubed) =
    DimensionCubed(dp3 = dp3 - dimension.dp3)

/**
 * Divide a DimensionCubed by a scalar.
 */
/*inline*/ operator fun DimensionCubed.div(by: Float): DimensionCubed =
    DimensionCubed(dp3 = dp3 / by)

/**
 * Divide by a [Dimension] to get a [DimensionSquared] result.
 */
/*inline*/ operator fun DimensionCubed.div(by: Dimension): DimensionSquared =
    DimensionSquared(dp2 = dp3 / by.dp)

/**
 * Divide by a [DimensionSquared] to get a [Dimension] result.
 */
/*inline*/ operator fun DimensionCubed.div(by: DimensionSquared): Dimension =
    Dimension(dp = dp3 / by.dp2)

/**
 * Divide by a DimensionCubed to get a scalar result.
 */
/*inline*/ operator fun DimensionCubed.div(by: DimensionCubed): Float = dp3 / by.dp3

/**
 * Multiply by a scalar to get a DimensionCubed result.
 */
/*inline*/ operator fun DimensionCubed.times(by: Float): DimensionCubed =
    DimensionCubed(dp3 = dp3 * by)

/**
 * Support comparing DimensionCubed with comparison operators.
 */
/*inline*/ operator fun DimensionCubed.compareTo(other: DimensionCubed) = dp3.compareTo(other.dp3)

/**
 * Holds a unit of an inverse dimensions, such as `1.dp / (2.dp * 3.dp)`. [DimensionSquared],
 * [DimensionCubed], and [DimensionInverse] are used primarily for [Dimension] calculations to
 * ensure resulting units are as expected. Many times, [Dimension] calculations use scalars to
 * determine the final dimension during calculation:
 *     val width = oldWidth * stretchAmount
 * Other times, it is useful to do intermediate calculations with Dimensions directly:
 *     val width = oldWidth * newTotalWidth / oldTotalWidth
 */
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class DimensionInverse(val idp: Float)

/**
 * Add two DimensionInverse together.
 */
/*inline*/ operator fun DimensionInverse.plus(dimension: DimensionInverse) =
    DimensionInverse(idp = idp + dimension.idp)

/**
 * Subtract a DimensionInverse from another one.
 */
/*inline*/ operator fun DimensionInverse.minus(dimension: DimensionInverse) =
    DimensionInverse(idp = idp - dimension.idp)

/**
 * Divide a DimensionInverse by a scalar.
 */
/*inline*/ operator fun DimensionInverse.div(by: Float): DimensionInverse =
    DimensionInverse(idp = idp / by)

/**
 * Multiply by a scalar to get a DimensionInverse result.
 */
/*inline*/ operator fun DimensionInverse.times(by: Float): DimensionInverse =
    DimensionInverse(idp = idp * by)

/**
 * Multiply by a [Dimension] to get a scalar result.
 */
/*inline*/ operator fun DimensionInverse.times(by: Dimension): Float = idp * by.dp

/**
 * Multiply by a [DimensionSquared] to get a [Dimension] result.
 */
/*inline*/ operator fun DimensionInverse.times(by: DimensionSquared): Dimension =
    Dimension(dp = idp * by.dp2)

/**
 * Multiply by a [DimensionCubed] to get a [DimensionSquared] result.
 */
/*inline*/ operator fun DimensionInverse.times(by: DimensionCubed): DimensionSquared =
    DimensionSquared(dp2 = idp * by.dp3)

/**
 * Support comparing DimensionInverse with comparison operators.
 */
/*inline*/ operator fun DimensionInverse.compareTo(other: DimensionInverse) =
    idp.compareTo(other.idp)

/**
 * A size in Pixels
 */
data class PixelSize(val width: Float, val height: Float)

/**
 * A four dimensional bounds using [Dimension] for units
 */
data class Bounds(
    val left: Dimension,
    val top: Dimension,
    val right: Dimension,
    val bottom: Dimension
)

/**
 * A width of this Bounds in [Dimension].
 */
val Bounds.width: Dimension get() = right - left

/**
 * A height of this Bounds in [Dimension].
 */
val Bounds.height: Dimension get() = bottom - top

/**
 * Convert a [Bounds] to a [Size].
 */
fun Bounds.toSize(): Size {
    return Size(width, height)
}

/**
 * Convert a [Size] to a [Bounds].
 */
fun Size.toBounds(): Bounds {
    return Bounds(0.dp, 0.dp, width, height)
}