package io.github.qingshu.ncnn.testapp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import io.github.qingshu.ncnn.Mat;
import io.github.qingshu.ncnn.Net;
import io.github.qingshu.ncnn.Extractor;
import io.github.qingshu.ncnn.Ncnn;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public class NcnnAndroidTest {
    private static final String PARAM =
            "7767517\n" +
            "3 3\n" +
            "Input in0 0 1 data\n" +
            "Convolution conv0 1 1 data conv 0=1 1=1 5=1 6=1\n" +
            "ReLU relu0 1 1 conv output\n";

    private static byte[] modelBytes() {
        // weight: tag 0 + float 2.0; bias: raw float 0.0
        return new byte[] {
                0, 0, 0, 0,
                0, 0, 0, 0x40,
                0, 0, 0, 0,
        };
    }

    @Test
    public void versionIsAvailable() {
        assertNotNull(Ncnn.INSTANCE.getVersion());
        assertTrue(Ncnn.INSTANCE.getVersionNumber() > 0);
    }

    @Test
    public void runsInferenceThroughJni() throws Exception {
        try (Net net = Net.Companion.create()) {
            net.loadParam(PARAM.getBytes(StandardCharsets.UTF_8));
            net.loadModel(modelBytes());
            assertEquals(1, net.getInputCount());
            assertEquals(1, net.getOutputCount());

            try (Mat input = Mat.Companion.fromFloats(
                    new float[] {-1f, 2f, -3f, 4f}, 2, 2, 1)) {
                Mat output;
                try (Extractor extractor = net.createExtractor()) {
                    extractor.input("data", input);
                    output = extractor.extract("output");
                }
                try (Mat result = output) {
                    assertArrayEquals(
                            new float[] {0f, 4f, 0f, 8f},
                            result.readFloats(),
                            0.0001f);
                }
            }
        }
    }
}
