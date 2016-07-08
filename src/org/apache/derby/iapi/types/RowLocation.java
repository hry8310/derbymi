/*

   Derby - Class org.apache.derby.iapi.types.RowLocation

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

package org.apache.derby.iapi.types;

/**

  Holds the location of a row within a given conglomerate.
  A row location is not valid except in the conglomerate
  from which it was obtained.  They are used to identify
  rows for fetches, deletes, and updates through a 
  conglomerate controller.
  <p>
  See the conglomerate implementation specification for
  information about the conditions under which a row location
  remains valid.

**/

public interface RowLocation extends DataValueDescriptor
{
}
