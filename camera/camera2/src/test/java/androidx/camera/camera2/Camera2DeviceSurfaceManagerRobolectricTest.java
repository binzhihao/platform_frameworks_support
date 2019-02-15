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

package androidx.camera.camera2;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.when;
import static org.robolectric.RuntimeEnvironment.application;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Build.VERSION_CODES;
import android.util.Rational;
import android.util.Size;
import android.view.WindowManager;

import androidx.camera.core.AppConfiguration;
import androidx.camera.core.BaseUseCase;
import androidx.camera.core.CameraDeviceSurfaceManager;
import androidx.camera.core.CameraFactory;
import androidx.camera.core.CameraX;
import androidx.camera.core.CameraX.LensFacing;
import androidx.camera.core.ExtendableUseCaseConfigFactory;
import androidx.camera.core.ImageAnalysisUseCaseConfiguration;
import androidx.camera.core.ImageCaptureUseCase;
import androidx.camera.core.ImageCaptureUseCaseConfiguration;
import androidx.camera.core.ImageFormatConstants;
import androidx.camera.core.SurfaceCombination;
import androidx.camera.core.SurfaceConfiguration;
import androidx.camera.core.SurfaceConfiguration.ConfigurationSize;
import androidx.camera.core.SurfaceConfiguration.ConfigurationType;
import androidx.camera.core.VideoCaptureUseCase;
import androidx.camera.core.VideoCaptureUseCaseConfiguration;
import androidx.camera.core.ViewFinderUseCase;
import androidx.camera.core.ViewFinderUseCaseConfiguration;
import androidx.camera.testing.StreamConfigurationMapUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.internal.DoNotInstrument;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowCameraCharacteristics;
import org.robolectric.shadows.ShadowCameraManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Robolectric test for {@link Camera2DeviceSurfaceManager} class */
@RunWith(RobolectricTestRunner.class)
@DoNotInstrument
@Config(minSdk = VERSION_CODES.LOLLIPOP)
public final class Camera2DeviceSurfaceManagerRobolectricTest {
    private static final String LEGACY_CAMERA_ID = "0";
    private static final String LIMITED_CAMERA_ID = "1";
    private static final String FULL_CAMERA_ID = "2";
    private static final String LEVEL3_CAMERA_ID = "3";
    private final Size mDisplaySize = new Size(1280, 720);
    private final Size mAnalysisSize = new Size(640, 480);
    private final Size mPreviewSize = mDisplaySize;
    private final Size mRecordSize = new Size(3840, 2160);
    private final Size mMaximumSize = new Size(4032, 3024);
    private final Size mMaximumVideoSize = new Size(1920, 1080);
    private final CamcorderProfileHelper mMockCamcorderProfileHelper =
            Mockito.mock(CamcorderProfileHelper.class);

    /**
     * Except for ImageFormat.JPEG or ImageFormat.YUV, other image formats will be mapped to
     * ImageFormat.PRIVATE (0x22) including SurfaceTexture or MediaCodec classes. Before Android
     * level 23, there is no ImageFormat.PRIVATE. But there is same internal code 0x22 for internal
     * corresponding format HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED. Therefore, set 0x22 as default
     * image formate.
     */
    private final int[] mSupportedFormats =
            new int[]{
                    ImageFormat.YUV_420_888,
                    ImageFormatConstants.INTERNAL_DEFINED_IMAGE_FORMAT_JPEG,
                    ImageFormatConstants.INTERNAL_DEFINED_IMAGE_FORMAT_PRIVATE
            };

    private final Size[] mSupportedSizes =
            new Size[]{
                    new Size(4032, 3024),
                    new Size(3840, 2160),
                    new Size(1920, 1080),
                    new Size(1280, 720),
                    new Size(640, 480),
                    new Size(320, 240),
                    new Size(320, 180)
            };

    private final Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private CameraDeviceSurfaceManager mSurfaceManager;

    @Before
    public void setUp() {
        WindowManager windowManager =
                (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        Shadows.shadowOf(windowManager.getDefaultDisplay()).setRealWidth(mDisplaySize.getWidth());
        Shadows.shadowOf(windowManager.getDefaultDisplay()).setRealHeight(mDisplaySize.getHeight());

        when(mMockCamcorderProfileHelper.hasProfile(anyInt(), anyInt())).thenReturn(true);

        setupCamera();
    }

    @Test
    public void checkLegacySurfaceCombinationSupportedInLegacyDevice() {
        SupportedSurfaceCombination supportedSurfaceCombination =
                new SupportedSurfaceCombination(
                        mContext, LEGACY_CAMERA_ID, mMockCamcorderProfileHelper);

        List<SurfaceCombination> combinationList =
                supportedSurfaceCombination.getLegacySupportedCombinationList();

        for (SurfaceCombination combination : combinationList) {
            boolean isSupported =
                    mSurfaceManager.checkSupported(
                            LEGACY_CAMERA_ID, combination.getSurfaceConfigurationList());
            assertTrue(isSupported);
        }
    }

    @Test
    public void checkLimitedSurfaceCombinationNotSupportedInLegacyDevice() {
        SupportedSurfaceCombination supportedSurfaceCombination =
                new SupportedSurfaceCombination(
                        mContext, LEGACY_CAMERA_ID, mMockCamcorderProfileHelper);

        List<SurfaceCombination> combinationList =
                supportedSurfaceCombination.getLimitedSupportedCombinationList();

        for (SurfaceCombination combination : combinationList) {
            boolean isSupported =
                    mSurfaceManager.checkSupported(
                            LEGACY_CAMERA_ID, combination.getSurfaceConfigurationList());
            assertFalse(isSupported);
        }
    }

    @Test
    public void checkFullSurfaceCombinationNotSupportedInLegacyDevice() {
        SupportedSurfaceCombination supportedSurfaceCombination =
                new SupportedSurfaceCombination(
                        mContext, LEGACY_CAMERA_ID, mMockCamcorderProfileHelper);

        List<SurfaceCombination> combinationList =
                supportedSurfaceCombination.getFullSupportedCombinationList();

        for (SurfaceCombination combination : combinationList) {
            boolean isSupported =
                    mSurfaceManager.checkSupported(
                            LEGACY_CAMERA_ID, combination.getSurfaceConfigurationList());
            assertFalse(isSupported);
        }
    }

    @Test
    public void checkLevel3SurfaceCombinationNotSupportedInLegacyDevice() {
        SupportedSurfaceCombination supportedSurfaceCombination =
                new SupportedSurfaceCombination(
                        mContext, LEGACY_CAMERA_ID, mMockCamcorderProfileHelper);

        List<SurfaceCombination> combinationList =
                supportedSurfaceCombination.getLevel3SupportedCombinationList();

        for (SurfaceCombination combination : combinationList) {
            boolean isSupported =
                    mSurfaceManager.checkSupported(
                            LEGACY_CAMERA_ID, combination.getSurfaceConfigurationList());
            assertFalse(isSupported);
        }
    }

    @Test
    public void checkLimitedSurfaceCombinationSupportedInLimitedDevice() {
        SupportedSurfaceCombination supportedSurfaceCombination =
                new SupportedSurfaceCombination(
                        mContext, LIMITED_CAMERA_ID, mMockCamcorderProfileHelper);

        List<SurfaceCombination> combinationList =
                supportedSurfaceCombination.getLimitedSupportedCombinationList();

        for (SurfaceCombination combination : combinationList) {
            boolean isSupported =
                    mSurfaceManager.checkSupported(
                            LIMITED_CAMERA_ID, combination.getSurfaceConfigurationList());
            assertTrue(isSupported);
        }
    }

    @Test
    public void checkFullSurfaceCombinationNotSupportedInLimitedDevice() {
        SupportedSurfaceCombination supportedSurfaceCombination =
                new SupportedSurfaceCombination(
                        mContext, LIMITED_CAMERA_ID, mMockCamcorderProfileHelper);

        List<SurfaceCombination> combinationList =
                supportedSurfaceCombination.getFullSupportedCombinationList();

        for (SurfaceCombination combination : combinationList) {
            boolean isSupported =
                    mSurfaceManager.checkSupported(
                            LIMITED_CAMERA_ID, combination.getSurfaceConfigurationList());
            assertFalse(isSupported);
        }
    }

    @Test
    public void checkLevel3SurfaceCombinationNotSupportedInLimitedDevice() {
        SupportedSurfaceCombination supportedSurfaceCombination =
                new SupportedSurfaceCombination(
                        mContext, LIMITED_CAMERA_ID, mMockCamcorderProfileHelper);

        List<SurfaceCombination> combinationList =
                supportedSurfaceCombination.getLevel3SupportedCombinationList();

        for (SurfaceCombination combination : combinationList) {
            boolean isSupported =
                    mSurfaceManager.checkSupported(
                            LIMITED_CAMERA_ID, combination.getSurfaceConfigurationList());
            assertFalse(isSupported);
        }
    }

    @Test
    public void checkFullSurfaceCombinationSupportedInFullDevice() {
        SupportedSurfaceCombination supportedSurfaceCombination =
                new SupportedSurfaceCombination(
                        mContext, FULL_CAMERA_ID, mMockCamcorderProfileHelper);

        List<SurfaceCombination> combinationList =
                supportedSurfaceCombination.getFullSupportedCombinationList();

        for (SurfaceCombination combination : combinationList) {
            boolean isSupported =
                    mSurfaceManager.checkSupported(
                            FULL_CAMERA_ID, combination.getSurfaceConfigurationList());
            assertTrue(isSupported);
        }
    }

    @Test
    public void checkLevel3SurfaceCombinationNotSupportedInFullDevice() {
        SupportedSurfaceCombination supportedSurfaceCombination =
                new SupportedSurfaceCombination(
                        mContext, FULL_CAMERA_ID, mMockCamcorderProfileHelper);

        List<SurfaceCombination> combinationList =
                supportedSurfaceCombination.getLevel3SupportedCombinationList();

        for (SurfaceCombination combination : combinationList) {
            boolean isSupported =
                    mSurfaceManager.checkSupported(
                            FULL_CAMERA_ID, combination.getSurfaceConfigurationList());
            assertFalse(isSupported);
        }
    }

    @Test
    public void checkLevel3SurfaceCombinationSupportedInLevel3Device() {
        SupportedSurfaceCombination supportedSurfaceCombination =
                new SupportedSurfaceCombination(
                        mContext, LEVEL3_CAMERA_ID, mMockCamcorderProfileHelper);

        List<SurfaceCombination> combinationList =
                supportedSurfaceCombination.getLevel3SupportedCombinationList();

        for (SurfaceCombination combination : combinationList) {
            boolean isSupported =
                    mSurfaceManager.checkSupported(
                            LEVEL3_CAMERA_ID, combination.getSurfaceConfigurationList());
            assertTrue(isSupported);
        }
    }

    @Test
    public void suggestedResolutionsForMixedUseCaseNotSupportedInLegacyDevice() {
        Rational aspectRatio = new Rational(16, 9);
        ViewFinderUseCaseConfiguration.Builder viewFinderConfigBuilder =
                new ViewFinderUseCaseConfiguration.Builder();
        VideoCaptureUseCaseConfiguration.Builder videoCaptureConfigBuilder =
                new VideoCaptureUseCaseConfiguration.Builder();
        ImageCaptureUseCaseConfiguration.Builder imageCaptureConfigBuilder =
                new ImageCaptureUseCaseConfiguration.Builder();

        viewFinderConfigBuilder.setTargetAspectRatio(aspectRatio);
        videoCaptureConfigBuilder.setTargetAspectRatio(aspectRatio);
        imageCaptureConfigBuilder.setTargetAspectRatio(aspectRatio);

        imageCaptureConfigBuilder.setLensFacing(LensFacing.BACK);
        ImageCaptureUseCase imageCaptureUseCase =
                new ImageCaptureUseCase(imageCaptureConfigBuilder.build());
        videoCaptureConfigBuilder.setLensFacing(LensFacing.BACK);
        VideoCaptureUseCase videoCaptureUseCase =
                new VideoCaptureUseCase(videoCaptureConfigBuilder.build());
        viewFinderConfigBuilder.setLensFacing(LensFacing.BACK);
        ViewFinderUseCase viewFinderUseCase =
                new ViewFinderUseCase(viewFinderConfigBuilder.build());

        List<BaseUseCase> useCases = new ArrayList<>();
        useCases.add(imageCaptureUseCase);
        useCases.add(videoCaptureUseCase);
        useCases.add(viewFinderUseCase);

        boolean exceptionHappened = false;

        try {
            // Will throw IllegalArgumentException
            mSurfaceManager.getSuggestedResolutions(LEGACY_CAMERA_ID, null, useCases);
        } catch (IllegalArgumentException e) {
            exceptionHappened = true;
        }

        assertTrue(exceptionHappened);
    }

    @Test
    public void getSuggestedResolutionsForMixedUseCaseInLimitedDevice() {
        Rational aspectRatio = new Rational(16, 9);
        ViewFinderUseCaseConfiguration.Builder viewFinderConfigBuilder =
                new ViewFinderUseCaseConfiguration.Builder();
        VideoCaptureUseCaseConfiguration.Builder videoCaptureConfigBuilder =
                new VideoCaptureUseCaseConfiguration.Builder();
        ImageCaptureUseCaseConfiguration.Builder imageCaptureConfigBuilder =
                new ImageCaptureUseCaseConfiguration.Builder();

        viewFinderConfigBuilder.setTargetAspectRatio(aspectRatio);
        videoCaptureConfigBuilder.setTargetAspectRatio(aspectRatio);
        imageCaptureConfigBuilder.setTargetAspectRatio(aspectRatio);

        imageCaptureConfigBuilder.setLensFacing(LensFacing.BACK);
        ImageCaptureUseCase imageCaptureUseCase =
                new ImageCaptureUseCase(imageCaptureConfigBuilder.build());
        videoCaptureConfigBuilder.setLensFacing(LensFacing.BACK);
        VideoCaptureUseCase videoCaptureUseCase =
                new VideoCaptureUseCase(videoCaptureConfigBuilder.build());
        viewFinderConfigBuilder.setLensFacing(LensFacing.BACK);
        ViewFinderUseCase viewFinderUseCase =
                new ViewFinderUseCase(viewFinderConfigBuilder.build());

        List<BaseUseCase> useCases = new ArrayList<>();
        useCases.add(imageCaptureUseCase);
        useCases.add(videoCaptureUseCase);
        useCases.add(viewFinderUseCase);
        Map<BaseUseCase, Size> suggestedResolutionMap =
                mSurfaceManager.getSuggestedResolutions(LIMITED_CAMERA_ID, null, useCases);

        // (PRIV, PREVIEW) + (PRIV, RECORD) + (JPEG, RECORD)
        assertThat(suggestedResolutionMap).containsEntry(imageCaptureUseCase, mRecordSize);
        assertThat(suggestedResolutionMap).containsEntry(videoCaptureUseCase, mMaximumVideoSize);
        assertThat(suggestedResolutionMap).containsEntry(viewFinderUseCase, mPreviewSize);
    }

    @Test
    public void transformSurfaceConfigurationWithYUVAnalysisSize() {
        SurfaceConfiguration surfaceConfiguration =
                mSurfaceManager.transformSurfaceConfiguration(
                        LEGACY_CAMERA_ID, ImageFormat.YUV_420_888, mAnalysisSize);
        SurfaceConfiguration expectedSurfaceConfiguration =
                SurfaceConfiguration.create(ConfigurationType.YUV, ConfigurationSize.ANALYSIS);
        assertEquals(expectedSurfaceConfiguration, surfaceConfiguration);
    }

    @Test
    public void transformSurfaceConfigurationWithYUVPreviewSize() {
        SurfaceConfiguration surfaceConfiguration =
                mSurfaceManager.transformSurfaceConfiguration(
                        LEGACY_CAMERA_ID, ImageFormat.YUV_420_888, mPreviewSize);
        SurfaceConfiguration expectedSurfaceConfiguration =
                SurfaceConfiguration.create(ConfigurationType.YUV, ConfigurationSize.PREVIEW);
        assertEquals(expectedSurfaceConfiguration, surfaceConfiguration);
    }

    @Test
    public void transformSurfaceConfigurationWithYUVRecordSize() {
        SurfaceConfiguration surfaceConfiguration =
                mSurfaceManager.transformSurfaceConfiguration(
                        LEGACY_CAMERA_ID, ImageFormat.YUV_420_888, mRecordSize);
        SurfaceConfiguration expectedSurfaceConfiguration =
                SurfaceConfiguration.create(ConfigurationType.YUV, ConfigurationSize.RECORD);
        assertEquals(expectedSurfaceConfiguration, surfaceConfiguration);
    }

    @Test
    public void transformSurfaceConfigurationWithYUVMaximumSize() {
        SurfaceConfiguration surfaceConfiguration =
                mSurfaceManager.transformSurfaceConfiguration(
                        LEGACY_CAMERA_ID, ImageFormat.YUV_420_888, mMaximumSize);
        SurfaceConfiguration expectedSurfaceConfiguration =
                SurfaceConfiguration.create(ConfigurationType.YUV, ConfigurationSize.MAXIMUM);
        assertEquals(expectedSurfaceConfiguration, surfaceConfiguration);
    }

    @Test
    public void transformSurfaceConfigurationWithYUVNotSupportSize() {
        SurfaceConfiguration surfaceConfiguration =
                mSurfaceManager.transformSurfaceConfiguration(
                        LEGACY_CAMERA_ID,
                        ImageFormat.YUV_420_888,
                        new Size(mMaximumSize.getWidth() + 1, mMaximumSize.getHeight() + 1));
        SurfaceConfiguration expectedSurfaceConfiguration =
                SurfaceConfiguration.create(ConfigurationType.YUV, ConfigurationSize.NOT_SUPPORT);
        assertEquals(expectedSurfaceConfiguration, surfaceConfiguration);
    }

    @Test
    public void transformSurfaceConfigurationWithJPEGAnalysisSize() {
        SurfaceConfiguration surfaceConfiguration =
                mSurfaceManager.transformSurfaceConfiguration(
                        LEGACY_CAMERA_ID, ImageFormat.JPEG, mAnalysisSize);
        SurfaceConfiguration expectedSurfaceConfiguration =
                SurfaceConfiguration.create(ConfigurationType.JPEG, ConfigurationSize.ANALYSIS);
        assertEquals(expectedSurfaceConfiguration, surfaceConfiguration);
    }

    @Test
    public void transformSurfaceConfigurationWithJPEGPreviewSize() {
        SurfaceConfiguration surfaceConfiguration =
                mSurfaceManager.transformSurfaceConfiguration(
                        LEGACY_CAMERA_ID, ImageFormat.JPEG, mPreviewSize);
        SurfaceConfiguration expectedSurfaceConfiguration =
                SurfaceConfiguration.create(ConfigurationType.JPEG, ConfigurationSize.PREVIEW);
        assertEquals(expectedSurfaceConfiguration, surfaceConfiguration);
    }

    @Test
    public void transformSurfaceConfigurationWithJPEGRecordSize() {
        SurfaceConfiguration surfaceConfiguration =
                mSurfaceManager.transformSurfaceConfiguration(
                        LEGACY_CAMERA_ID, ImageFormat.JPEG, mRecordSize);
        SurfaceConfiguration expectedSurfaceConfiguration =
                SurfaceConfiguration.create(ConfigurationType.JPEG, ConfigurationSize.RECORD);
        assertEquals(expectedSurfaceConfiguration, surfaceConfiguration);
    }

    @Test
    public void transformSurfaceConfigurationWithJPEGMaximumSize() {
        SurfaceConfiguration surfaceConfiguration =
                mSurfaceManager.transformSurfaceConfiguration(
                        LEGACY_CAMERA_ID, ImageFormat.JPEG, mMaximumSize);
        SurfaceConfiguration expectedSurfaceConfiguration =
                SurfaceConfiguration.create(ConfigurationType.JPEG, ConfigurationSize.MAXIMUM);
        assertEquals(expectedSurfaceConfiguration, surfaceConfiguration);
    }

    @Test
    public void transformSurfaceConfigurationWithJPEGNotSupportSize() {
        SurfaceConfiguration surfaceConfiguration =
                mSurfaceManager.transformSurfaceConfiguration(
                        LEGACY_CAMERA_ID,
                        ImageFormat.JPEG,
                        new Size(mMaximumSize.getWidth() + 1, mMaximumSize.getHeight() + 1));
        SurfaceConfiguration expectedSurfaceConfiguration =
                SurfaceConfiguration.create(ConfigurationType.JPEG, ConfigurationSize.NOT_SUPPORT);
        assertEquals(expectedSurfaceConfiguration, surfaceConfiguration);
    }

    @Test
    public void getMaximumSizeForImageFormat() {
        Size maximumYUVSize =
                mSurfaceManager.getMaxOutputSize(LEGACY_CAMERA_ID, ImageFormat.YUV_420_888);
        assertEquals(mMaximumSize, maximumYUVSize);
        Size maximumJPEGSize = mSurfaceManager.getMaxOutputSize(LEGACY_CAMERA_ID, ImageFormat.JPEG);
        assertEquals(mMaximumSize, maximumJPEGSize);
    }

    private void setupCamera() {
        addBackFacingCamera(
                LEGACY_CAMERA_ID, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY, null);
        addBackFacingCamera(
                LIMITED_CAMERA_ID,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
                null);
        addBackFacingCamera(
                FULL_CAMERA_ID, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL, null);
        addBackFacingCamera(
                LEVEL3_CAMERA_ID, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3, null);
        initCameraX();
    }

    private void addBackFacingCamera(String cameraId, int hardwareLevel, int[] capabilities) {
        CameraCharacteristics characteristics =
                ShadowCameraCharacteristics.newCameraCharacteristics();

        ShadowCameraCharacteristics shadowCharacteristics = Shadow.extract(characteristics);

        shadowCharacteristics.set(
                CameraCharacteristics.LENS_FACING, CameraCharacteristics.LENS_FACING_BACK);

        shadowCharacteristics.set(
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL, hardwareLevel);

        if (capabilities != null) {
            shadowCharacteristics.set(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES, capabilities);
        }

        ((ShadowCameraManager) Shadow.extract(application.getSystemService(Context.CAMERA_SERVICE)))
                .addCamera(cameraId, characteristics);

        shadowCharacteristics.set(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP,
                StreamConfigurationMapUtil.generateFakeStreamConfigurationMap(
                        mSupportedFormats, mSupportedSizes));
    }

    private void initCameraX() {
        AppConfiguration appConfig = createFakeAppConfiguration();
        CameraX.init(mContext, appConfig);
        mSurfaceManager = CameraX.getSurfaceManager();
    }

    private AppConfiguration createFakeAppConfiguration() {

        // Create the camera factory for creating Camera2 camera objects
        CameraFactory cameraFactory = new Camera2CameraFactory(mContext);

        // Create the DeviceSurfaceManager for Camera2
        CameraDeviceSurfaceManager surfaceManager =
                new Camera2DeviceSurfaceManager(mContext, mMockCamcorderProfileHelper);

        // Create default configuration factory
        ExtendableUseCaseConfigFactory configFactory = new ExtendableUseCaseConfigFactory();
        configFactory.installDefaultProvider(
                ImageAnalysisUseCaseConfiguration.class,
                new DefaultImageAnalysisConfigurationProvider(cameraFactory));
        configFactory.installDefaultProvider(
                ImageCaptureUseCaseConfiguration.class,
                new DefaultImageCaptureConfigurationProvider(cameraFactory));
        configFactory.installDefaultProvider(
                VideoCaptureUseCaseConfiguration.class,
                new DefaultVideoCaptureConfigurationProvider(cameraFactory));
        configFactory.installDefaultProvider(
                ViewFinderUseCaseConfiguration.class,
                new DefaultViewFinderConfigurationProvider(cameraFactory));

        AppConfiguration.Builder appConfigBuilder =
                new AppConfiguration.Builder()
                        .setCameraFactory(cameraFactory)
                        .setDeviceSurfaceManager(surfaceManager)
                        .setUseCaseConfigFactory(configFactory);

        return appConfigBuilder.build();
    }
}