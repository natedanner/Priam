/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.priam.tuner;

import static org.junit.Assert.*;

import com.netflix.priam.config.FakeConfiguration;
import com.netflix.priam.config.IConfiguration;
import com.netflix.priam.scheduler.UnsupportedTypeException;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.Test;

/** Created by aagrawal on 8/29/17. */
public class JVMOptionTunerTest {
    private IConfiguration config;
    JVMOptionsTuner tuner;

    @Test
    public void testCMS() throws Exception {
        config = new GCConfiguration(GCType.CMS, null, null, null, null);
        List<JVMOption> jvmOptionMap = getConfiguredJVMVersionOptions(config);
        // Validate that all CMS options should be uncommented.
        long failedVerification =
                jvmOptionMap
                        .stream()
                        .map(
                                jvmOption -> {
                                    GCType gcType = GCTuner.getGCType(jvmOption);
                                    if (gcType != null && gcType != GCType.CMS) {
                                        return 1;
                                    }
                                    return 0;
                                })
                        .filter(returncode -> (returncode != 0))
                        .count();

        if (failedVerification > 0) throw new Exception("Failed validation for CMS");
    }

    @Test
    public void testG1GC() throws Exception {
        config = new GCConfiguration(GCType.G1GC, null, null, null, null);
        List<JVMOption> jvmOptionMap = getConfiguredJVMVersionOptions(config);
        // Validate that all G1GC options should be uncommented.
        long failedVerification =
                jvmOptionMap
                        .stream()
                        .map(
                                jvmOption -> {
                                    GCType gcType = GCTuner.getGCType(jvmOption);
                                    if (gcType != null && gcType != GCType.G1GC) {
                                        return 1;
                                    }
                                    return 0;
                                })
                        .filter(returncode -> (returncode != 0))
                        .count();

        if (failedVerification > 0) throw new Exception("Failed validation for G1GC");
    }

    @Test
    public void testCMSUpsert() throws Exception {
        JVMOption option1 = new JVMOption("-Dsample");
        JVMOption option2 = new JVMOption("-Dsample2", "10", false, false);
        JVMOption option3 = new JVMOption("-XX:NumberOfGCLogFiles", "20", false, false);

        JVMOption xmnOption = new JVMOption("-Xmn", "3G", false, true);
        JVMOption xmxOption = new JVMOption("-Xmx", "20G", false, true);
        JVMOption xmsOption = new JVMOption("-Xms", "20G", false, true);

        StringBuffer buffer =
                new StringBuffer(
                        option1.toJVMOptionString()
                                + ","
                                + option2.toJVMOptionString()
                                + ","
                                + option3.toJVMOptionString());
        config =
                new GCConfiguration(
                        GCType.CMS,
                        null,
                        buffer.toString(),
                        xmnOption.getValue(),
                        xmxOption.getValue());
        List<JVMOption> jvmOptions = getConfiguredJVMOptions(config);
        List<JVMOption> jvmVersionOptions = getConfiguredJVMVersionOptions(config);

        // Verify all the options do exist.
        assertTrue(jvmVersionOptions.contains(option3));
        assertTrue(jvmOptions.contains(option2));
        assertTrue(jvmOptions.contains(option1));

        // Verify heap options exist with the value provided.
        assertTrue(jvmOptions.contains(xmnOption));
        assertTrue(jvmOptions.contains(xmxOption));
        assertTrue(jvmOptions.contains(xmsOption));
    }

    @Test
    public void testCMSExclude() throws Exception {
        JVMOption youngHeap = new JVMOption("-Xmn", "3G", false, true);
        JVMOption maxHeap = new JVMOption("-Xmx", "12G", false, true);

        JVMOption option1 = new JVMOption("-XX:+UseParNewGC");
        JVMOption option2 = new JVMOption("-XX:NumberOfGCLogFiles", "20", false, false);
        JVMOption option3 = new JVMOption("-XX:+UseG1GC", null, false, false);

        StringBuffer buffer =
                new StringBuffer(
                        option1.toJVMOptionString()
                                + ","
                                + option2.toJVMOptionString()
                                + ","
                                + option3.toJVMOptionString());
        config = new GCConfiguration(GCType.CMS, buffer.toString(), null, "3G", "12G");
        List<JVMOption> jvmOptions = getConfiguredJVMOptions(config);
        List<JVMOption> jvmVersionOptions = getConfiguredJVMVersionOptions(config);

        // Verify all the options do not exist.
        assertFalse(jvmVersionOptions.contains(option3));
        assertFalse(jvmVersionOptions.contains(option2));
        assertFalse(jvmVersionOptions.contains(option1));

        // Verify that Xmn is present since CMS needs tuning of young gen heap
        assertTrue(jvmOptions.contains(maxHeap));
        assertTrue(jvmOptions.contains(youngHeap));
    }

    @Test
    public void testG1GCUpsertExclude() throws Exception {
        JVMOption youngHeap = new JVMOption("-Xmn", "3G", true, true);
        JVMOption maxHeap = new JVMOption("-Xmx", "12G", false, true);

        JVMOption option1 = new JVMOption("-Dsample");
        JVMOption option2 = new JVMOption("-Dsample2", "10", false, false);
        JVMOption option3 = new JVMOption("-XX:NumberOfGCLogFiles", "20", false, false);
        StringBuffer upsert =
                new StringBuffer(
                        option1.toJVMOptionString()
                                + ","
                                + option2.toJVMOptionString()
                                + ","
                                + option3.toJVMOptionString());

        JVMOption option4 = new JVMOption("-XX:NumberOfGCLogFiles", null, false, false);
        JVMOption option5 = new JVMOption("-XX:+UseG1GC", null, false, false);
        StringBuffer exclude =
                new StringBuffer(option4.toJVMOptionString() + "," + option5.toJVMOptionString());

        config =
                new GCConfiguration(
                        GCType.G1GC, exclude.toString(), upsert.toString(), "3G", "12G");
        List<JVMOption> jvmOptions = getConfiguredJVMOptions(config);
        List<JVMOption> jvmVersionOptions = getConfiguredJVMVersionOptions(config);

        // Verify upserts exist
        assertTrue(jvmOptions.contains(option1));
        assertTrue(jvmOptions.contains(option2));

        // Verify exclude exist. This is to prove that if an element is in EXCLUDE, it will always
        // be excluded.
        assertFalse(jvmVersionOptions.contains(option3));
        assertFalse(jvmVersionOptions.contains(option4));
        assertFalse(jvmVersionOptions.contains(option5));

        // Verify that Xmn is not present since G1GC autotunes the young gen heap
        assertTrue(jvmOptions.contains(maxHeap));
        assertFalse(jvmOptions.contains(youngHeap));

        List<JVMOption> allJVMOptions = getConfiguredJVMOptions(config, false);
        assertTrue(allJVMOptions.contains(youngHeap));
    }

    @Test
    public void testInject() throws Exception {
        JVMOption youngHeap = new JVMOption("-Xmn", "3G", true, true);
        JVMOption maxHeap = new JVMOption("-Xmx", "12G", false, true);

        JVMOption option1 = new JVMOption("-Dsample");
        JVMOption option2 = new JVMOption("-Dsample2", "10", false, false);
        StringBuffer upsert =
                new StringBuffer(option1.toJVMOptionString() + "," + option2.toJVMOptionString());

        String upsertString =
                "-Dcassandra.schema_delay_ms=60000 -Dcassandra.skip_schema_check_for_versions=ver1,ver2";
        config = new GCConfiguration(GCType.G1GC, "", upsert.toString(), "3G", "12G", upsertString);

        JVMOptionsTuner tuner = new JVMOptionsTuner(config);
        List<String> configuredJVMOptions = tuner.updateJVMOptions().get("configuredJVMOptions");

        for (String s :
                new String[] {
                    option1.toJVMOptionString(), option2.toJVMOptionString(), upsertString
                }) {
            boolean found = false;
            for (String f : configuredJVMOptions) {
                if (f.equalsIgnoreCase(s)) {
                    found = true;
                    break;
                }
            }

            assertTrue("could not find " + s + " in jvm options", found);
        }
    }

    private List<JVMOption> getConfiguredJVMOptions(IConfiguration config) throws Exception {
        return getConfiguredJVMOptions(config, true);
    }

    private List<JVMOption> getConfiguredJVMOptions(IConfiguration config, boolean filter)
            throws Exception {
        tuner = new JVMOptionsTuner(config);
        Map<String, List<String>> options = tuner.updateJVMOptions();
        List<String> configuredJVMOptions = options.get("configuredJVMOptions");
        if (filter) {
            return configuredJVMOptions
                    .stream()
                    .map(JVMOption::parse)
                    .filter(jvmOption -> (jvmOption != null))
                    .filter(jvmOption -> !jvmOption.isCommented())
                    .collect(Collectors.toList());
        } else {
            return configuredJVMOptions.stream().map(JVMOption::parse).collect(Collectors.toList());
        }
    }

    private List<JVMOption> getConfiguredJVMVersionOptions(IConfiguration config) throws Exception {
        return getConfiguredJVMVersionOptions(config, true);
    }

    private List<JVMOption> getConfiguredJVMVersionOptions(IConfiguration config, boolean filter)
            throws Exception {
        tuner = new JVMOptionsTuner(config);
        Map<String, List<String>> options = tuner.updateJVMOptions();
        List<String> configuredJVMVersionOptions = options.get("configuredJVMVersionOptions");
        if (filter) {
            return configuredJVMVersionOptions
                    .stream()
                    .map(JVMOption::parse)
                    .filter(jvmOption -> (jvmOption != null))
                    .filter(jvmOption -> !jvmOption.isCommented())
                    .collect(Collectors.toList());
        } else {
            return configuredJVMVersionOptions
                    .stream()
                    .map(JVMOption::parse)
                    .collect(Collectors.toList());
        }
    }

    private class GCConfiguration extends FakeConfiguration {
        private GCType gcType;
        private String configuredJVMExclude;
        private String configuredJVMUpsert;
        private String configuredHeapNewSize;
        private String configuredHeapSize;

        private String configuredJVMInject;

        GCConfiguration(
                GCType gcType,
                String configuredJVMExclude,
                String configuredJVMUpsert,
                String configuredHeapNewSize,
                String configuredHeapSize) {
            this(
                    gcType,
                    configuredJVMExclude,
                    configuredJVMUpsert,
                    configuredHeapNewSize,
                    configuredHeapSize,
                    "");
        }

        GCConfiguration(
                GCType gcType,
                String configuredJVMExclude,
                String configuredJVMUpsert,
                String configuredHeapNewSize,
                String configuredHeapSize,
                String configuredJVMInject) {
            this.gcType = gcType;
            this.configuredJVMExclude = configuredJVMExclude;
            this.configuredJVMUpsert = configuredJVMUpsert;
            this.configuredHeapNewSize = configuredHeapNewSize;
            this.configuredHeapSize = configuredHeapSize;
            this.configuredJVMInject = configuredJVMInject;
        }

        @Override
        public GCType getGCType() throws UnsupportedTypeException {
            return gcType;
        }

        @Override
        public String getJVMExcludeSet() {
            return configuredJVMExclude;
        }

        @Override
        public String getHeapSize() {
            return configuredHeapSize;
        }

        @Override
        public String getHeapNewSize() {
            return configuredHeapNewSize;
        }

        @Override
        public String getJVMUpsertSet() {
            return configuredJVMUpsert;
        }

        @Override
        public String getJVMInjectSet() {
            return configuredJVMInject;
        }
    }
}
