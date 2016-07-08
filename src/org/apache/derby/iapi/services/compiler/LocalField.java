/*

   Derby - Class org.apache.derby.iapi.services.compiler.LocalField

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

package org.apache.derby.iapi.services.compiler;

/**
	A field within the generated class.
 */
public interface LocalField {

	/**
		Return an expression that's the value of this field
	 */
	//Expression getField();

	/**
		Return an expression that assigns the passed
		in value to the field and returns the value set.
	 */
	//Expression putField(Expression value);
}
