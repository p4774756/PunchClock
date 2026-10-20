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
    public void buildLabelShowsVersionAndShortSourceDigest() {
        String digest = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";
        assertEquals("v2.0.2 · a1b2c3d", BuildInfo.buildLabel("2.0.2", digest));
        assertEquals("v2.0.2 · a1b2c3d", BuildInfo.buildLabel("2.0.2", digest.toUpperCase()));
        assertEquals("沒有摘要就只顯示版號", "v2.0.2", BuildInfo.buildLabel("2.0.2", ""));
        assertEquals("v2.0.2", BuildInfo.buildLabel("2.0.2", "${sourceDigest}"));
        assertEquals("新版本 · a1b2c3d", BuildInfo.buildLabel("", digest));
        assertEquals("a1b2c3d", BuildInfo.shortDigest(digest));
        assertEquals("", BuildInfo.shortDigest("zzz"));

        Properties props = new Properties();
        props.setProperty("version", "2.0.2");
        props.setProperty("sourceDigest", digest);
        assertEquals("v2.0.2 · a1b2c3d", BuildInfo.fromProperties(props).buildLabel());
    }

    @Test
    public void sameSourceGivesSameLabelRegardlessOfBuildTime() {
        String digest = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";
        Properties first = new Properties();
        first.setProperty("version", "2.0.2");
        first.setProperty("sourceDigest", digest);
        first.setProperty("buildTime", "2026-10-20T07:58:00Z");
        Properties second = new Properties();
        second.setProperty("version", "2.0.2");
        second.setProperty("sourceDigest", digest);
        second.setProperty("buildTime", "2026-10-21T01:00:00Z");

        assertEquals(BuildInfo.fromProperties(first).buildLabel(), BuildInfo.fromProperties(second).buildLabel());
    }

    @Test
    public void labelFromIsoFormatsInZoneAndToleratesGarbage() {
        assertEquals("2026-09-24 15:58", BuildInfo.labelFromIso("2026-09-24T07:58:00Z", BuildInfo.TAIPEI));
        assertEquals("", BuildInfo.labelFromIso("", BuildInfo.TAIPEI));
        assertEquals("", BuildInfo.labelFromIso(null, BuildInfo.TAIPEI));
        assertEquals("", BuildInfo.labelFromIso("not-a-time", BuildInfo.TAIPEI));
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
    public void sourceDigestKeepsLowerHexAndDropsPlaceholders() {
        Properties props = new Properties();
        props.setProperty("version", "1.8.0");
        props.setProperty("sourceDigest", "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789");
        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                BuildInfo.fromProperties(props).sourceDigest());

        props.setProperty("sourceDigest", "${punchclock.sourceDigest}");
        assertEquals("", BuildInfo.fromProperties(props).sourceDigest());
        assertEquals("", BuildInfo.normalizeDigest("not-a-digest"));
    }

    @Test
    public void missingResourceFallsBackToDev() {
        BuildInfo info = BuildInfo.load(BuildInfoTest.class, "/no-such-build.properties");
        assertEquals(BuildInfo.DEV_VERSION, info.version());
        assertEquals("", info.buildTimeIso());
    }
}
