/*

   Derby - Class org.apache.derby.iapi.services.io.SQLExceptionWrapper

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

package org.apache.derby.iapi.services.io;

import org.apache.derby.shared.common.sanity.SanityManager;

import java.sql.SQLException;
import java.io.IOException;

/**
    Wrapper class for SQLExceptions
 */
class SQLExceptionWrapper extends SQLException
{
    private Exception myException;

    SQLExceptionWrapper(Exception e)
    {
        myException = e;
    }

    void handleMe()
        throws IOException, ClassNotFoundException
    {
        if (myException instanceof IOException)
        {
            throw ((IOException) myException);
        }
        else if (myException instanceof ClassNotFoundException)
        {
            throw ((ClassNotFoundException) myException);
        }

        if (SanityManager.DEBUG)
        {
            SanityManager.NOTREACHED();
        }
    }

    void handleMeToo()
        throws IOException
    {
        if (myException instanceof IOException)
        {
            throw ((IOException) myException);
        }

        if (SanityManager.DEBUG)
        {
            SanityManager.NOTREACHED();
        }
    }


}
