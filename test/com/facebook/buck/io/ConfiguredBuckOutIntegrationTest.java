/*
 * Copyright 2016-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.io;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.testutil.integration.DebuggableTemporaryFolder;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.google.common.base.Splitter;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfiguredBuckOutIntegrationTest {

  private ProjectWorkspace workspace;

  @Rule
  public DebuggableTemporaryFolder tmp = new DebuggableTemporaryFolder();

  @Before
  public void setUp() throws IOException {
    workspace = TestDataHelper.createProjectWorkspaceForScenario(this, "configured_buck_out", tmp);
    workspace.setUp();
  }

  @Test
  public void outputPathsUseConfiguredBuckOut() throws IOException {
    String buckOut = "new-buck-out";
    Path output = workspace.buildAndReturnOutput("-c", "project.buck_out=" + buckOut, "//:dummy");
    assertTrue(Files.exists(output));
    assertThat(workspace.getDestPath().relativize(output).toString(), Matchers.startsWith(buckOut));
  }

  @Test
  public void configuredBuckOutAffectsRuleKey() throws IOException {
    String out =
        workspace.runBuckCommand("targets", "--show-rulekey", "//:dummy")
            .assertSuccess()
            .getStdout();
    String ruleKey = Splitter.on(' ').splitToList(out).get(1);
    String configuredOut =
        workspace.runBuckCommand(
            "targets", "--show-rulekey", "-c", "project.buck_out=something", "//:dummy")
            .assertSuccess()
            .getStdout();
    String configuredRuleKey = Splitter.on(' ').splitToList(configuredOut).get(1);
    assertThat(ruleKey, Matchers.not(Matchers.equalTo(configuredRuleKey)));
  }

}
