package com.example;

import org.junit.Test;

import java.time.ZoneId;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BuildInfoTest {

    @Test
    public void formatsFilteredBuildTimeInGivenZone() {
        Properties props = new Properties();
        props.setProperty("version", "1.8.0");
        props.setProperty("buildTime", "2026-09-24T07:58:00Z");
        BuildInfo info = BuildInfo.fromProperties(props);

        assertEquals("1.8.0", info.version());
        assertEquals("2026-09-24T07:58:00Z", info.buildTimeIso());
        assertEquals("2026-09-24 15:58", info.buildTimeLabel(BuildInfo.TAIPEI));
        assertEquals("2026-09-24 07:58", info.buildTimeLabel(ZoneId.of("UTC")));
    }

    @Test
    public void releaseTimePrefersCommitTime() {
        Properties props = new Properties();
        props.setProperty("version", "1.8.0");
        props.setProperty("buildTime", "2026-09-24T07:58:00Z");
        props.setProperty("commitTime", "2026-09-24T07:53:06Z");
        BuildInfo info = BuildInfo.fromProperties(props);

        assertTrue(info.hasCommitTime());
        assertEquals("2026-09-24T07:53:06Z", info.releaseTimeIso());
        assertEquals("2026-09-24 15:53", info.releaseTimeLabel(BuildInfo.TAIPEI));
        assertEquals("2026-09-24T07:58:00Z", info.buildTimeIso());
    }

    @Test
    public void releaseTimeFallsBackToBuildTimeWithoutGit() {
        Properties props = new Properties();
        props.setProperty("version", "1.8.0");
        props.setProperty("buildTime", "2026-09-24T07:58:00Z");
        props.setProperty("commitTime", "${git.commit.time}");
        BuildInfo info = BuildInfo.fromProperties(props);

        assertFalse(info.hasCommitTime());
        assertEquals("2026-09-24T07:58:00Z", info.releaseTimeIso());
    }

    @Test
    public void unfilteredPlaceholdersFallBackToDev() {
        Properties props = new Properties();
        props.setProperty("version", "${project.version}");
        props.setProperty("buildTime", "${punchclock.buildTime}");
        BuildInfo info = BuildInfo.fromProperties(props);

        assertEquals(BuildInfo.DEV_VERSION, info.version());
        assertEquals("", info.buildTimeIso());
        assertEquals("", info.buildTimeLabel(BuildInfo.TAIPEI));
        assertEquals("", info.releaseTimeIso());
    }

    @Test
    public void missingResourceFallsBackToDev() {
        BuildInfo info = BuildInfo.load(BuildInfoTest.class, "/no-such-build.properties");
        assertEquals(BuildInfo.DEV_VERSION, info.version());
        assertEquals("", info.buildTimeIso());
    }
}
