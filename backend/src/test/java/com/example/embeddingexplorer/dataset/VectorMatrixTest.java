package com.example.embeddingexplorer.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class VectorMatrixTest {

    @Test
    void writesFloat32LittleEndianRowByRow() throws IOException {
        VectorMatrix matrix = VectorMatrix.of(new float[] {1.5f, -2f, 0f}, new float[] {0.25f, 3f, -0.125f});

        byte[] bytes = written(matrix);

        assertThat(bytes).hasSize(6 * Float.BYTES);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] values = new float[6];
        buffer.asFloatBuffer().get(values);
        assertThat(values).containsExactly(1.5f, -2f, 0f, 0.25f, 3f, -0.125f);
        // 1.5f is 0x3FC00000: little-endian puts the least significant byte first.
        assertThat(bytes[0]).isZero();
        assertThat(bytes[3]).isEqualTo((byte) 0x3F);
    }

    @Test
    void writesMatricesLargerThanTheBufferCompletely() throws IOException {
        SplittableRandom random = new SplittableRandom(7);
        float[][] rows = new float[30][1_536];
        for (float[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                row[i] = (float) random.nextGaussian();
            }
        }
        VectorMatrix matrix = VectorMatrix.of(rows);

        byte[] bytes = written(matrix);

        assertThat((long) bytes.length).isEqualTo(matrix.byteSize());
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(buffer.getFloat(0)).isEqualTo(rows[0][0]);
        assertThat(buffer.getFloat((29 * 1_536 + 1_535) * Float.BYTES)).isEqualTo(rows[29][1_535]);
    }

    @Test
    void anEmptyMatrixWritesNothing() throws IOException {
        assertThat(written(VectorMatrix.empty())).isEmpty();
    }

    @Test
    void computesDotProductsAndDistancesBetweenRows() {
        VectorMatrix matrix = VectorMatrix.of(new float[] {1f, 2f, 2f}, new float[] {3f, 0f, -4f});

        assertThat(matrix.dot(0, 1)).isCloseTo(3 - 8, within(1e-12));
        assertThat(matrix.dot(0, 0)).isCloseTo(9, within(1e-12));
        assertThat(matrix.squaredDistance(0, 1)).isCloseTo(4 + 4 + 36, within(1e-12));
        assertThat(matrix.squaredDistance(1, 1)).isZero();
    }

    private static byte[] written(VectorMatrix matrix) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        matrix.writeLittleEndian(out);
        return out.toByteArray();
    }
}
