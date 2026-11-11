package exportimport
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.activemq.artemis.cli.commands.ActionContext
import  org.apache.activemq.artemis.cli.commands.tools.XmlDataExporter

File pagingfile = new File(arg[0] + "/sender/data/paging")
pagingfile.mkdirs()
XmlDataExporter exporter = new XmlDataExporter();
exporter.binding = arg[0] + "/sender/data/bindings"
exporter.journal = arg[0] + "/sender/data/journal"
try {
    exporter.largeMessages = arg[0] + "/sender/data/largemessages"
} catch (Throwable e) {
    exporter.largeMessges = arg[0] + "/sender/data/largemessages"
}
exporter.paging = arg[0] + "/sender/data/paging"


PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(arg[0] + "/journal.export")));
exporter.execute(new ActionContext(System.in, out, System.err))
out.close();
//exporter.binding
