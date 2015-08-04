/**
 * Copyright 2007 The European Bioinformatics Institute, and others.
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
 *  limitations under the License.
 */
package uk.ac.ebi.intact.bridges.wswublast;

import org.junit.Test;
import org.junit.Assert;

import java.io.File;

/**
 * TODO comment that class header
 *
 * @author Irina Armean (iarmean@ebi.ac.uk)
 * @version $Id$
 * @since TODO specify the maven artifact version
 *        <pre>
 *        30-Nov-2007
 *        </pre>
 */
public class BlastConfigTest {
    
    @Test
    public void testGetWorkDir() throws Exception {
        BlastConfig blastConf = new BlastConfig("myName@yahaa.com");
        File archiveDir = blastConf.getBlastArchiveDir();
        Assert.assertNotNull( archiveDir);
        File blastDb = blastConf.getDatabaseDir();
        Assert.assertNotNull( blastDb);

        String tableName = blastConf.getTableName();
        Assert.assertEquals( "job", tableName);

        int nr = blastConf.getNrPerSubmission();
        Assert.assertEquals( 20, nr);

        String email = blastConf.getEmail();
        Assert.assertEquals( "myName@yahaa.com", email);
    }

}