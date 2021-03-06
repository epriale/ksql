/*
 * Copyright 2020 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"; you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.tools.migrations.commands;

import com.github.rvesse.airline.annotations.Arguments;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.restrictions.Required;

@Command(
    name = "new",
    description = "Creates a new migrations project, directory structure and config file."
)
public class NewMigrationCommand extends BaseCommand {

  @Required
  @Arguments(description = "the project path to create the directory", title = "project-path")
  private String projectPath;

  @Override
  public void run() {
    throw new UnsupportedOperationException();
  }

}
