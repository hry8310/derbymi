/*

   Derby - Class org.apache.derby.shared.common.sanity.SanityState

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.shared.common.sanity;

/*
 **
 * This class is intended for the use of the SanityManager in conjunction
 * with making a build either Sane or Insane. An insane build is one which
 * has the two booleans expressed here as "false"; a sane build should be
 * have the booleans expressed as "true".
 *
 * @see org.apache.derby.iapi.services.sanity.SanityManager
 ** 
*/

public class SanityState
{
	public static final boolean ASSERT=true ;
	public static final boolean DEBUG=true ;
}
