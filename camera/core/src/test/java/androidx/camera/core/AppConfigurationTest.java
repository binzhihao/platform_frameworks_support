/*
 * Copyright (C) 2019 The Android Open Source Project
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

package androidx.camera.core;

import static com.google.common.truth.Truth.assertThat;

import android.os.Build;

import androidx.camera.testing.fakes.FakeAppConfiguration;
import androidx.camera.testing.fakes.FakeCameraDeviceSurfaceManager;
import androidx.camera.testing.fakes.FakeCameraFactory;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.internal.DoNotInstrument;

@SmallTest
@RunWith(RobolectricTestRunner.class)
@DoNotInstrument
// TODO(b/124267925): Bump down to LOLLIPOP once our minSdk is 21
@Config(minSdk = Build.VERSION_CODES.N)
public class AppConfigurationTest {

    private AppConfiguration mAppConfiguration;

    @Before
    public void setUp() {
        mAppConfiguration = FakeAppConfiguration.create();
    }

    @Test
    public void canGetConfigTarget() {
        Class<CameraX> configTarget = mAppConfiguration.getTargetClass(/*valueIfMissing=*/ null);
        assertThat(configTarget).isEqualTo(CameraX.class);
    }

    @Test
    public void canGetCameraFactory() {
        CameraFactory cameraFactory = mAppConfiguration.getCameraFactory(/*valueIfMissing=*/ null);
        assertThat(cameraFactory).isInstanceOf(FakeCameraFactory.class);
    }

    @Test
    public void canGetDeviceSurfaceManager() {
        CameraDeviceSurfaceManager surfaceManager =
                mAppConfiguration.getDeviceSurfaceManager(/*valueIfMissing=*/ null);
        assertThat(surfaceManager).isInstanceOf(FakeCameraDeviceSurfaceManager.class);
    }
}