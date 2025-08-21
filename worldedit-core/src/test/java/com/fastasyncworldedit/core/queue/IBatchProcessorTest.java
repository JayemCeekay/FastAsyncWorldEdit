package com.fastasyncworldedit.core.queue;

import com.fastasyncworldedit.core.queue.implementation.blocks.DataArray;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@Disabled
class IBatchProcessorTest {

    @Nested
    @Isolated
    class trimY {

        private static final int AIR = BlockTypesCache.ReservedIDs.AIR;
        private static final int RESERVED = BlockTypesCache.ReservedIDs.__RESERVED__;
        private final IBatchProcessor processor = new NoopBatchProcessor();

        @ParameterizedTest
        @MethodSource("provideTrimYInBoundsParameters")
        void testFullChunkSelectedInBoundedRegion(int minY, int maxY, int minSection, int maxSection) {
            final IChunkSet set = mock();

            final int minSectionPos = -64 >> 4; // -4
            final int maxSectionPos = 319 >> 4; // 19
            final int offset = 4; // to handle negative indices

            DataArray[] sections = new DataArray[(320 + 64) >> 4];
            for (int i = 0; i < sections.length; i++) {
                DataArray arr = DataArray.createEmpty();
                arr.setAll(AIR);
                sections[i] = arr;
            }

            when(set.getMinSectionPosition()).thenReturn(minSectionPos);
            when(set.getMaxSectionPosition()).thenReturn(maxSectionPos);
            when(set.hasSection(anyInt())).thenReturn(true);
            when(set.loadIfPresent(anyInt())).thenAnswer(inv -> {
                int section = inv.getArgument(0);
                return sections[section + offset];
            });
            doAnswer(inv -> {
                int section = inv.getArgument(0);
                DataArray data = inv.getArgument(1);
                sections[section + offset] = data; // may be null
                return null;
            }).when(set).setBlocks(anyInt(), any());

            processor.trimY(set, minY, maxY, true);

            for (int section = minSectionPos; section <= maxSectionPos; section++) {
                int idx = section + offset;
                DataArray palette = sections[idx];
                if (section < minSection) {
                    assertNull(palette, "expected section below minimum section to be null");
                    continue;
                }
                if (section > maxSection) {
                    assertNull(palette, "expected section above maximum section to be null");
                    continue;
                }
                assertNotNull(palette, "expected section " + section + " to be non-null");

                if (section == minSection) {
                    for (int slice = 0; slice < 16; slice++) {
                        boolean shouldContainBlocks = slice >= (minY & 15);
                        // If boundaries only span one section, the upper constraints have to be checked explicitly
                        if (section == maxSection) {
                            shouldContainBlocks &= slice <= (maxY & 15);
                        }
                        assertSliceEquals(
                                palette, slice, shouldContainBlocks ? AIR : RESERVED,
                                "[lower] slice %d (y=%d) expected to contain ".formatted(slice, ((section << 4) + slice)) + (shouldContainBlocks ? "air" : "nothing")
                        );
                    }
                    continue;
                }
                if (section == maxSection) {
                    for (int slice = 0; slice < 16; slice++) {
                        boolean shouldContainBlocks = slice <= (maxY & 15);
                        assertSliceEquals(
                                palette, slice, shouldContainBlocks ? AIR : RESERVED,
                                "[upper] slice %d (y=%d) expected to contain ".formatted(slice, ((section << 4) + slice)) + (shouldContainBlocks ? "air" : "nothing")
                        );
                    }
                    continue;
                }
                // fully enclosed sections should remain full AIR
                assertFullSectionEquals(palette, AIR, "full captured chunk @ %d should contain full data".formatted(section));
            }

        }

        private static void assertSliceEquals(DataArray arr, int slice, int expectedValue, String message) {
            int start = slice << 8;
            int end = (slice + 1) << 8;
            for (int i = start; i < end; i++) {
                assertEquals(expectedValue, arr.getAt(i), message + " (index " + i + ")");
            }
        }

        private static void assertFullSectionEquals(DataArray arr, int expectedValue, String message) {
            for (int i = 0; i < DataArray.CHUNK_SECTION_SIZE; i++) {
                assertEquals(expectedValue, arr.getAt(i), message + " (index " + i + ")");
            }
        }

        /**
         * Arguments explained:
         * 1. minimum y coordinate (inclusive)
         * 2. maximum y coordinate (inclusive)
         * 3. chunk section which contains minimum y coordinate
         * 4. chunk section which contains maximum y coordinate
         */
        private static Stream<Arguments> provideTrimYInBoundsParameters() {
            return Stream.of(
                    Arguments.of(64, 72, 4, 4),
                    Arguments.of(-64, 0, -4, 0),
                    Arguments.of(0, 128, 0, 8),
                    Arguments.of(16, 132, 1, 8),
                    Arguments.of(4, 144, 0, 9),
                    Arguments.of(12, 255, 0, 15),
                    Arguments.of(24, 103, 1, 6)
            );
        }

    }

    private static final class NoopBatchProcessor implements IBatchProcessor {

        @Override
        public IChunkSet processSet(final IChunk chunk, final IChunkGet get, final IChunkSet set) {
            return set;
        }

        @Override
        public @Nullable Extent construct(final Extent child) {
            return null;
        }

    }

}
