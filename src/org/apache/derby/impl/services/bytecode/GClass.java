/*

   Derby - Class org.apache.derby.impl.services.bytecode.GClass

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.impl.services.bytecode;

import org.apache.derby.iapi.services.compiler.ClassBuilder; 
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.shared.common.sanity.SanityManager;
import org.apache.derby.iapi.services.stream.HeaderPrintWriter; 

import org.apache.derby.iapi.util.ByteArray;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * This is a common superclass for the various impls.
 * Saving class files is a common thing to do.
 *
 */
public abstract class GClass implements ClassBuilder {

	protected ByteArray bytecode;  



	 
	 

	protected void writeClassFile(String dir, boolean logMessage, Throwable t)
		throws StandardException {

		if (SanityManager.DEBUG) {

		if (bytecode ==  null) getClassBytecode(); // not recursing...

		if (dir == null) dir="";

		String filename = getName(); // leave off package

		filename = filename + ".class";

		final File classFile = new File(dir,filename);

		FileOutputStream fos = null;
		 
		}
	}

	final void validateType(String typeName1)
	{
	    if (SanityManager.DEBUG)
	    {
            SanityManager.ASSERT(typeName1 != null);

            String typeName = typeName1.trim();

            if ("void".equals(typeName)) return;

	        // first remove all array-ness
	        while (typeName.endsWith("[]")) typeName = typeName.substring(0,typeName.length()-2);

            SanityManager.ASSERT(typeName.length() > 0);

	        // then check for primitive types
	        if ("boolean".equals(typeName)) return;
	        if ("byte".equals(typeName)) return;
	        if ("char".equals(typeName)) return;
	        if ("double".equals(typeName)) return;
	        if ("float".equals(typeName)) return;
	        if ("int".equals(typeName)) return;
	        if ("long".equals(typeName)) return;
	        if ("short".equals(typeName)) return;

	        // then see if it can be found
	        // REVISIT: this will fail if ASSERT is on and the
	        // implementation at hand is missing the target type.
	        // We do plan to generate classes against
	        // different implementations from the compiler's implementation
	        // at some point...
			 

	        // all the checks succeeded, it must be okay.
	        return;
	    }
	}
}
