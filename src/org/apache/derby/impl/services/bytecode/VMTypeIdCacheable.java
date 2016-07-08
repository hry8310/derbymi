/*

   Derby - Class org.apache.derby.impl.services.bytecode.VMTypeIdCacheable

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

 
 

import org.apache.derby.shared.common.sanity.SanityManager;
 

/**
 * This class implements a Cacheable for a Byte code generator cache of
 * VMTypeIds.  It maps a Java class or type name to a VM type ID.
 */
class VMTypeIdCacheable  {
	/* The VM name of the Java class name */
	// either a Type (java type) or a String (method descriptor)
	private Object descriptor;

	/* This is the identity */
	private Object key;

	/* Cacheable interface */

	/** @see Cacheable#clearIdentity */
	public void clearIdentity() {
	}

	/** @see Cacheable#getIdentity */
	public Object getIdentity() {
		return key;
	}

	  
	/** @see Cacheable#clean */
	public void clean(boolean remove) {
		/* No such thing as a dirty cache entry */
		return;
	}

	/** @see Cacheable#isDirty */
	public boolean isDirty() {
		/* No such thing as a dirty cache entry */
		return false;
	}

	/*
	** Class specific methods.
	*/

	/**
	 * Get the VM Type name (java/lang/Object) that is associated with this Cacheable
	 */

	Object descriptor() {
		return descriptor;
	}
}
