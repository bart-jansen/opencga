/*
 * Copyright 2015-2017 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.storage.core.variant.stats;

import org.junit.*;
import org.junit.rules.ExpectedException;
import org.opencb.biodata.models.variant.StudyEntry;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.stats.VariantStats;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.storage.core.exceptions.StorageEngineException;
import org.opencb.opencga.storage.core.metadata.StudyConfiguration;
import org.opencb.opencga.storage.core.variant.VariantStorageBaseTest;
import org.opencb.opencga.storage.core.variant.VariantStorageEngine;
import org.opencb.opencga.storage.core.variant.VariantStorageEngineTest;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.*;

/**
 * Created by hpccoll1 on 01/06/15.
 */
@Ignore
public abstract class VariantStatisticsManagerTest extends VariantStorageBaseTest {

    public static final String VCF_TEST_FILE_NAME = "variant-test-file.vcf.gz";
    private StudyConfiguration studyConfiguration;
    private VariantDBAdaptor dbAdaptor;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void beforeClass() throws IOException {
        Path rootDir = getTmpRootDir();
        Path inputPath = rootDir.resolve(VCF_TEST_FILE_NAME);
        Files.copy(VariantStorageEngineTest.class.getClassLoader().getResourceAsStream(VCF_TEST_FILE_NAME), inputPath,
                StandardCopyOption.REPLACE_EXISTING);
        inputUri = inputPath.toUri();
    }

    @Override
    @Before
    public void before() throws Exception {
        studyConfiguration = newStudyConfiguration();
        clearDB(DB_NAME);
        runDefaultETL(inputUri, getVariantStorageEngine(), studyConfiguration,
                new ObjectMap(VariantStorageEngine.Options.ANNOTATE.key(), false));
        dbAdaptor = getVariantStorageEngine().getDBAdaptor();
    }

    @Test
    public void calculateStatsMultiCohortsTest() throws Exception {
        //Calculate stats for 2 cohorts at one time
        VariantStatisticsManager vsm = variantStorageEngine.newVariantStatisticsManager();

        checkCohorts(dbAdaptor, studyConfiguration);

        Integer fileId = studyConfiguration.getFileIds().get(Paths.get(inputUri).getFileName().toString());
        QueryOptions options = new QueryOptions(VariantStorageEngine.Options.FILE_ID.key(), fileId);
        options.put(VariantStorageEngine.Options.LOAD_BATCH_SIZE.key(), 100);
        Iterator<String> iterator = studyConfiguration.getSampleIds().keySet().iterator();

        /** Create cohorts **/
        HashSet<String> cohort1 = new HashSet<>();
        cohort1.add(iterator.next());
        cohort1.add(iterator.next());

        HashSet<String> cohort2 = new HashSet<>();
        cohort2.add(iterator.next());
        cohort2.add(iterator.next());

        Map<String, Set<String>> cohorts = new HashMap<>();
        cohorts.put("cohort1", cohort1);
        cohorts.put("cohort2", cohort2);

        //Calculate stats
        stats(options, studyConfiguration, cohorts, outputUri.resolve("cohort1.cohort2.stats"));

        checkCohorts(dbAdaptor, studyConfiguration);
    }

    @Test
    public void calculateStatsSeparatedCohortsTest() throws Exception {
        //Calculate stats for 2 cohorts separately

        String studyName = studyConfiguration.getStudyName();
        Integer fileId = studyConfiguration.getFileIds().get(Paths.get(inputUri).getFileName().toString());
        QueryOptions options = new QueryOptions(VariantStorageEngine.Options.FILE_ID.key(), fileId);
        options.put(VariantStorageEngine.Options.LOAD_BATCH_SIZE.key(), 100);
        Iterator<String> iterator = studyConfiguration.getSampleIds().keySet().iterator();
        StudyConfiguration studyConfiguration;

        /** Create first cohort **/
        studyConfiguration = dbAdaptor.getStudyConfigurationManager().getStudyConfiguration(studyName, QueryOptions.empty()).first();
        HashSet<String> cohort1 = new HashSet<>();
        cohort1.add(iterator.next());
        cohort1.add(iterator.next());

        Map<String, Set<String>> cohorts;

        cohorts = new HashMap<>();
        cohorts.put("cohort1", cohort1);

        //Calculate stats for cohort1
        studyConfiguration = stats(options, studyConfiguration, cohorts, outputUri.resolve("cohort1.stats"));

        int cohort1Id = studyConfiguration.getCohortIds().get("cohort1");
        assertThat(studyConfiguration.getCalculatedStats(), hasItem(cohort1Id));
        checkCohorts(dbAdaptor, studyConfiguration);

        /** Create second cohort **/
        studyConfiguration = dbAdaptor.getStudyConfigurationManager().getStudyConfiguration(studyName, QueryOptions.empty()).first();
        HashSet<String> cohort2 = new HashSet<>();
        cohort2.add(iterator.next());
        cohort2.add(iterator.next());

        cohorts = new HashMap<>();
        cohorts.put("cohort2", cohort2);

        //Calculate stats for cohort2
        studyConfiguration = stats(options, studyConfiguration, cohorts, outputUri.resolve("cohort2.stats"));

        int cohort2Id = studyConfiguration.getCohortIds().get("cohort2");
        assertThat(studyConfiguration.getCalculatedStats(), hasItem(cohort1Id));
        assertThat(studyConfiguration.getCalculatedStats(), hasItem(cohort2Id));

        checkCohorts(dbAdaptor, studyConfiguration);

        //Try to recalculate stats for cohort2. Will fail
        studyConfiguration = dbAdaptor.getStudyConfigurationManager().getStudyConfiguration(studyName, QueryOptions.empty()).first();
        thrown.expect(StorageEngineException.class);
        stats(options, studyConfiguration, cohorts, outputUri.resolve("cohort2.stats"));

    }

    public StudyConfiguration stats(QueryOptions options, StudyConfiguration studyConfiguration, Map<String, Set<String>> cohorts,
                                    URI output) throws IOException, StorageEngineException {
        options.put(DefaultVariantStatisticsManager.OUTPUT, output.toString());
        variantStorageEngine.calculateStats(studyConfiguration.getStudyName(), cohorts, options);
        return dbAdaptor.getStudyConfigurationManager().getStudyConfiguration(studyConfiguration.getStudyId(), null).first();
    }

    private static void checkCohorts(VariantDBAdaptor dbAdaptor, StudyConfiguration studyConfiguration) {
        for (Variant variant : dbAdaptor) {
            for (StudyEntry sourceEntry : variant.getStudies()) {
                Map<String, VariantStats> cohortStats = sourceEntry.getStats();
                String calculatedCohorts = cohortStats.keySet().toString();
                for (Map.Entry<String, Integer> entry : studyConfiguration.getCohortIds().entrySet()) {
                    assertTrue("CohortStats should contain stats for cohort " + entry.getKey() + ". Only contains stats for " +
                                    calculatedCohorts,
                            cohortStats.containsKey(entry.getKey()));    //Check stats are calculated

                    assertEquals("Stats have less genotypes than expected.",
                            studyConfiguration.getCohorts().get(entry.getValue()).size(),  //Check numGenotypes are correct (equals to
                            // the number of samples)
                            cohortStats.get(entry.getKey()).getGenotypesCount().values().stream().reduce(0, (a, b) -> a + b).intValue());
                }
            }
        }
    }

}
