/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.ui.layout

import androidx.ui.core.Constraints
import androidx.ui.core.Dimension
import androidx.ui.core.MeasureBox
import androidx.ui.core.coerceAtLeast
import androidx.ui.core.dp
import androidx.ui.core.min
import androidx.ui.core.minus
import androidx.ui.core.plus
import com.google.r4a.Children
import com.google.r4a.Composable
import com.google.r4a.composer

/**
 * Describes a set of offsets from each of the four sides of a box. For example,
 * it is used to describe padding for the [Padding] widget.
 */
data class EdgeInsets(
    val left: Dimension = 0.dp,
    val top: Dimension = 0.dp,
    val right: Dimension = 0.dp,
    val bottom: Dimension = 0.dp
) {
    constructor(all: Dimension) : this(all, all, all, all)
}

/**
 * Layout widget which takes a child composable and applies whitespace padding around it.
 * When passing layout constraints to its child, [Padding] shrinks the constraints by the
 * requested padding, causing the child to layout at a smaller size.
 *
 * Example usage:
 *     <Row>
 *         <Padding padding=EdgeInsets(right=20.dp)>
 *             <SizedRectangle color=Color(0xFFFF0000.toInt()) width=20.dp height= 20.dp />
 *         </Padding>
 *         <Padding padding=EdgeInsets(left=20.dp)>
 *             <SizedRectangle color=Color(0xFFFF0000.toInt()) width=20.dp height= 20.dp />
 *         </Padding>
 *     </Row>
 */
@Composable
fun Padding(
    padding: EdgeInsets,
    @Children children: () -> Unit
) {
    <MeasureBox> constraints, measureOperations ->
        val measurable = measureOperations.collect(children).firstOrNull()
        if (measurable == null) {
            measureOperations.layout(constraints.minWidth, constraints.maxWidth) {}
        } else {
            val horizontalPadding = (padding.left + padding.right)
            val verticalPadding = (padding.top + padding.bottom)

            val newConstraints = Constraints(
                minWidth = (constraints.minWidth - horizontalPadding).coerceAtLeast(0.dp),
                maxWidth = (constraints.maxWidth - horizontalPadding).coerceAtLeast(0.dp),
                minHeight = (constraints.minHeight - verticalPadding).coerceAtLeast(0.dp),
                maxHeight = (constraints.maxHeight - verticalPadding).coerceAtLeast(0.dp)
            )
            val placeable = measureOperations.measure(measurable, newConstraints)
            val width = min(placeable.width + horizontalPadding, constraints.maxWidth)
            val height = min(placeable.height + verticalPadding, constraints.maxHeight)

            measureOperations.layout(width, height) {
                placeable.place(padding.left, padding.top)
            }
        }
    </MeasureBox>
}