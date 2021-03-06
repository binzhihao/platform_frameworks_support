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

package androidx.ui.painting

// import androidx.ui.core.Duration
import androidx.ui.engine.text.ParagraphBuilder
import androidx.ui.engine.text.ParagraphStyle
import androidx.ui.engine.text.TextAffinity
import androidx.ui.engine.text.TextPosition
import androidx.ui.graphics.Color
// import androidx.ui.gestures.multitap.MultiTapGestureRecognizer
import androidx.ui.painting.basictypes.RenderComparison
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.anyString
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@RunWith(JUnit4::class)
class TextSpanTest {
    @Test
    fun `constructor with default values`() {
        val textSpan = TextSpan()

        assertThat(textSpan.style).isNull()
        assertThat(textSpan.text).isNull()
        assertThat(textSpan.children).isEqualTo(mutableListOf<TextSpan>())
        // assertThat(textSpan.recognizer).isNull()
    }

    @Test
    fun `constructor with customized style`() {
        val textStyle = TextStyle(fontSize = 10.0f, height = 123.0f)
        val textSpan = TextSpan(style = textStyle)

        assertThat(textSpan.style).isEqualTo(textStyle)
    }

    @Test
    fun `constructor with customized text`() {
        val string = "Hello"
        val textSpan = TextSpan(text = string)

        assertThat(textSpan.text).isEqualTo(string)
    }

    @Test
    fun `constructor with customized children`() {
        val string1 = "Hello"
        val string2 = "World"
        val textSpan1 = TextSpan(text = string1)
        val textSpan2 = TextSpan(text = string2)
        val textSpan = TextSpan(children = mutableListOf(textSpan1, textSpan2))

        assertThat(textSpan.children.size).isEqualTo(2)
        assertThat(textSpan.children.get(0).text).isEqualTo(string1)
        assertThat(textSpan.children.get(1).text).isEqualTo(string2)
    }

    /*@Test
    fun `constructor with customized recognizer`() {
        val recognizer = MultiTapGestureRecognizer(longTapDelay = Duration.zero)
        val textSpan = TextSpan(recognizer = recognizer)

        assertThat(textSpan.recognizer).isEqualTo(recognizer)
    }*/

    @Test
    fun `build without style`() {
        val textSpan = TextSpan()
        val mockBuilder = spy(ParagraphBuilder(ParagraphStyle()))

        textSpan.build(mockBuilder)

        verify(mockBuilder, times(0)).pushStyle(androidx.ui.engine.text.TextStyle())
        verify(mockBuilder, times(0)).pop()
    }

    @Test
    fun `build with style`() {
        val textStyle = TextStyle(fontSize = 10.0f, height = 123.0f)
        val textSpan = TextSpan(style = textStyle)
        val mockBuilder = spy(ParagraphBuilder(ParagraphStyle()))

        textSpan.build(mockBuilder)

        verify(mockBuilder, times(1)).pushStyle(textStyle.getTextStyle())
        verify(mockBuilder, times(1)).pop()
    }

    @Test
    fun `build with neither text nor children`() {
        val textSpan = TextSpan()
        val mockBuilder = spy(ParagraphBuilder(ParagraphStyle()))

        textSpan.build(mockBuilder)

        verify(mockBuilder, times(0)).addText(anyString())
    }

    @Test
    fun `build with text`() {
        val string = "Hello"
        val textSpan = TextSpan(text = string)
        val mockBuilder = spy(ParagraphBuilder(ParagraphStyle()))

        textSpan.build(mockBuilder)

        verify(mockBuilder, times(1)).addText(string)
    }

    @Test
    fun `build with children`() {
        val string1 = "Hello"
        val string2 = "World"
        val textSpan1 = TextSpan(text = string1)
        val textSpan2 = TextSpan(text = string2, children = mutableListOf(TextSpan(text = "abc")))
        val textSpan = TextSpan(children = mutableListOf(textSpan1, textSpan2))
        val mockBuilder = spy(ParagraphBuilder(ParagraphStyle()))

        textSpan.build(mockBuilder)

        verify(mockBuilder, times(1)).addText(string1)
        verify(mockBuilder, times(1)).addText(string2)
    }

    @Test
    fun `visitTextSpan with neither text nor children should return true`() {
        val textSpan = TextSpan()

        val result = textSpan.visitTextSpan { _: TextSpan -> false }

        assertThat(result).isTrue()
    }

    @Test
    fun `visitTextSpan with text and visitor always returns true`() {
        val textSpan = TextSpan(text = "Hello")

        val result = textSpan.visitTextSpan { _: TextSpan -> true }

        assertThat(result).isTrue()
    }

    @Test
    fun `visitTextSpan with text and visitor always returns false`() {
        val textSpan = TextSpan(text = "Hello")

        val result = textSpan.visitTextSpan { _: TextSpan -> false }

        assertThat(result).isFalse()
    }

    @Test
    fun `visitTextSpan with children and visitor always returns true`() {
        val textSpan1 = spy(TextSpan(text = "Hello"))
        val textSpan2 = spy(TextSpan(text = "World"))
        val textSpan = spy(TextSpan(children = mutableListOf(textSpan1, textSpan2)))
        val returnTrueFunction = { _: TextSpan -> true }

        val result = textSpan.visitTextSpan(returnTrueFunction)

        assertThat(result).isTrue()
        verify(textSpan1, times(1)).visitTextSpan(returnTrueFunction)
        verify(textSpan2, times(1)).visitTextSpan(returnTrueFunction)
    }

    @Test
    fun `visitTextSpan with children and visitor always returns false`() {
        val textSpan1 = spy(TextSpan(text = "Hello"))
        val textSpan2 = spy(TextSpan(text = "World"))
        val textSpan = TextSpan(children = mutableListOf(textSpan1, textSpan2))
        val returnFalseFunction = { _: TextSpan -> false }

        val result = textSpan.visitTextSpan(returnFalseFunction)

        assertThat(result).isFalse()
        verify(textSpan1, times(1)).visitTextSpan(returnFalseFunction)
        verify(textSpan2, times(0)).visitTextSpan(returnFalseFunction)
    }

    @Test
    fun `getSpanForPosition without text`() {
        val textSpan = TextSpan()
        val textPosition = TextPosition(0, TextAffinity.downstream)

        assertThat(textSpan.getSpanForPosition(textPosition)).isNull()
    }

    @Test
    fun `getSpanForPosition with text, 0 offset, and downstream TextAffinity`() {
        val textSpan = TextSpan(text = "Hello")
        val textPosition = TextPosition(0, TextAffinity.downstream)

        assertThat(textSpan.getSpanForPosition(textPosition)).isEqualTo(textSpan)
    }

    @Test
    fun `getSpanForPosition with text, and offset is smaller than text length`() {
        val string = "Hello"
        val textSpan = TextSpan(text = string)
        val textPosition = TextPosition(string.length - 1, TextAffinity.downstream)

        assertThat(textSpan.getSpanForPosition(textPosition)).isEqualTo(textSpan)
    }

    @Test
    fun `getSpanForPosition with text, offset = text length, and upstream TextAffinity`() {
        val string = "Hello"
        val textSpan = TextSpan(text = string)
        val textPosition = TextPosition(string.length, TextAffinity.upstream)

        assertThat(textSpan.getSpanForPosition(textPosition)).isEqualTo(textSpan)
    }

    @Test
    fun `getSpanForPosition with text, 0 offset, and upstream TextAffinity`() {
        val textSpan = TextSpan(text = "Hello")
        val textPosition = TextPosition(0, TextAffinity.upstream)

        assertThat(textSpan.getSpanForPosition(textPosition)).isNull()
    }

    @Test
    fun `getSpanForPosition with text, offset = text length, and downstream TextAffinity`() {
        val string = "Hello"
        val textSpan = TextSpan(text = string)
        val textPosition = TextPosition(string.length, TextAffinity.downstream)

        assertThat(textSpan.getSpanForPosition(textPosition)).isNull()
    }

    @Test
    fun `getSpanForPosition with text, offset is bigger than text length`() {
        val string = "Hello"
        val textSpan = TextSpan(text = string)
        val textPosition = TextPosition(string.length + 1, TextAffinity.upstream)

        assertThat(textSpan.getSpanForPosition(textPosition)).isNull()
    }

    @Test
    fun `toPlainText with neither text nor children`() {
        val textSpan = TextSpan()

        assertThat(textSpan.toPlainText()).isEmpty()
    }

    @Test
    fun `toPlainText with text`() {
        val string = "Hello"
        val textSpan = TextSpan(text = string)

        assertThat(textSpan.toPlainText()).isEqualTo(string)
    }

    @Test
    fun `toPlainText with children`() {
        val string1 = "Hello"
        val string2 = "World"
        val textSpan1 = TextSpan(text = string1)
        val textSpan2 = TextSpan(text = string2)
        val textSpan = TextSpan(children = mutableListOf(textSpan1, textSpan2))

        assertThat(textSpan.toPlainText()).isEqualTo(string1 + string2)
    }

    // TODO(Migration/qqd): Figure out what to do with codeUnitAt.
//    @Test
//    fun `codeUnitAt with negative index`() {
//        val textSpan = TextSpan(text = string1)
//
//        assertThat(textSpan.codeUnitAt(-1)).isNull()
//    }
//
//    @Test
//    fun `codeUnitAt with index larger than text length`() {
//        val textSpan = TextSpan(text = string1)
//
//        assertThat(textSpan.codeUnitAt(string1.length + 1)).isNull()
//    }
//
//    @Test
//    fun `codeUnitAt with valid text and index`() {
//        val textSpan = TextSpan(text = string1)
//        val index = 0
//
//        assertThat(textSpan.codeUnitAt(index)).isEqualTo(string1[index].toInt())
//    }

    @Test
    fun `compareTo self should return IDENTICAL`() {
        val textSpan = TextSpan()

        assertThat(textSpan.compareTo(textSpan)).isEqualTo(RenderComparison.IDENTICAL)
    }

    @Test
    fun `compareTo with different text should return LAYOUT`() {
        val textSpan1 = TextSpan(text = "Hello")
        val textSpan2 = TextSpan(text = "World")

        assertThat(textSpan1.compareTo(textSpan2)).isEqualTo(RenderComparison.LAYOUT)
    }

    @Test
    fun `compareTo with different children list length should return LAYOUT`() {
        val childTextSpan1 = TextSpan(text = "Hello")
        val childTextSpan2 = TextSpan(text = "World")
        val textSpan1 = TextSpan(children = mutableListOf(childTextSpan1))
        val textSpan2 = TextSpan(children = mutableListOf(childTextSpan1, childTextSpan2))

        assertThat(textSpan1.compareTo(textSpan2)).isEqualTo(RenderComparison.LAYOUT)
    }

    @Test
    fun `compareTo with one null style should return LAYOUT`() {
        val textSpan1 = TextSpan()
        val textSpan2 = TextSpan(style = TextStyle(height = 123.0f))

        assertThat(textSpan1.compareTo(textSpan2)).isEqualTo(RenderComparison.LAYOUT)
    }

    /*@Test
    fun `compareTo with same recognizer should return IDENTICAL`() {
        val recognizer1 = MultiTapGestureRecognizer(longTapDelay = Duration.zero)
        val textSpan1 = TextSpan(recognizer = recognizer1)
        val textSpan2 = TextSpan(recognizer = recognizer1)

        assertThat(textSpan1.compareTo(textSpan2)).isEqualTo(RenderComparison.IDENTICAL)
    }

    @Test
    fun `compareTo with different recognizer should return METADATA`() {
        val recognizer1 = MultiTapGestureRecognizer(longTapDelay = Duration.zero)
        val recognizer2 = MultiTapGestureRecognizer(longTapDelay = Duration.create(seconds = 1L))
        val textSpan1 = TextSpan(recognizer = recognizer1)
        val textSpan2 = TextSpan(recognizer = recognizer2)

        assertThat(textSpan1.compareTo(textSpan2)).isEqualTo(RenderComparison.METADATA)
    }*/

    @Test
    fun `compareTo with different TextStyle with different fontSize should return LAYOUT`() {
        val textStyle1 = TextStyle()
        val textStyle2 = TextStyle(fontSize = 10.0f)
        val textSpan1 = TextSpan(style = textStyle1)
        val textSpan2 = TextSpan(style = textStyle2)

        assertThat(textSpan1.compareTo(textSpan2)).isEqualTo(RenderComparison.LAYOUT)
    }

    @Test
    fun `compareTo with different TextStyle with different color should return PAINT`() {
        val textStyle1 = TextStyle(color = Color(0))
        val textStyle2 = TextStyle(color = Color(1))
        val textSpan1 = TextSpan(style = textStyle1)
        val textSpan2 = TextSpan(style = textStyle2)

        assertThat(textSpan1.compareTo(textSpan2)).isEqualTo(RenderComparison.PAINT)
    }

    @Test
    fun `compareTo with different children with different text should return LAYOUT`() {
        val childTextSpan1 = TextSpan(text = "Hello")
        val childTextSpan2 = TextSpan(text = "World")
        val textSpan1 = TextSpan(children = mutableListOf(childTextSpan1))
        val textSpan2 = TextSpan(children = mutableListOf(childTextSpan2))

        assertThat(textSpan1.compareTo(textSpan2)).isEqualTo(RenderComparison.LAYOUT)
    }

    @Test
    fun `compareTo with different children with different fontSize should return LAYOUT`() {
        val textStyle1 = TextStyle()
        val textStyle2 = TextStyle(fontSize = 10.0f)
        val childTextSpan1 = TextSpan(style = textStyle1)
        val childTextSpan2 = TextSpan(style = textStyle2)
        val textSpan1 = TextSpan(children = mutableListOf(childTextSpan1))
        val textSpan2 = TextSpan(children = mutableListOf(childTextSpan2))

        assertThat(textSpan1.compareTo(textSpan2)).isEqualTo(RenderComparison.LAYOUT)
    }

    @Test
    fun `compareTo with different children with different color should return PAINT`() {
        val textStyle1 = TextStyle(color = Color(0))
        val textStyle2 = TextStyle(color = Color(1))
        val childTextSpan1 = TextSpan(style = textStyle1)
        val childTextSpan2 = TextSpan(style = textStyle2)
        val textSpan1 = TextSpan(children = mutableListOf(childTextSpan1))
        val textSpan2 = TextSpan(children = mutableListOf(childTextSpan2))

        assertThat(textSpan1.compareTo(textSpan2)).isEqualTo(RenderComparison.PAINT)
    }

//    @Test
//    fun `compareTo with different children with different recognizer should return METADATA`() {
//        val recognizer1 = MultiTapGestureRecognizer(longTapDelay = Duration.zero)
//        val recognizer2 = MultiTapGestureRecognizer(longTapDelay = Duration.create(seconds = 1L))
//        val childTextSpan1 = TextSpan(recognizer = recognizer1)
//        val childTextSpan2 = TextSpan(recognizer = recognizer2)
//        val textSpan1 = TextSpan(children = mutableListOf(childTextSpan1))
//        val textSpan2 = TextSpan(children = mutableListOf(childTextSpan2))
//
//        assertThat(textSpan1.compareTo(textSpan2)).isEqualTo(RenderComparison.METADATA)
//    }
}
