/*

   Derby - Class org.apache.derby.impl.sql.compile.OptimizerTracer

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

package org.apache.dearbaby.impl.sql.compile;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.sql.SQLException; 
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.context.ContextService; 
import org.apache.derby.iapi.sql.compile.OptTrace;
import org.apache.derby.iapi.sql.dictionary.OptionalTool;

/**
 * <p>
 * OptionalTool for tracing the Optimizer.
 * </p>
 */
public	class   OptimizerTracer  implements OptionalTool
{
    ////////////////////////////////////////////////////////////////////////
    //
    //	CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    //
    //	STATE
    //
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    //
    //	CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////

    /** 0-arg constructor required by the OptionalTool contract */
    public  OptimizerTracer() {}

    ///////////////////////////////////////////////////////////////////////////////////
    //
    // OptionalTool BEHAVIOR
    //
    ///////////////////////////////////////////////////////////////////////////////////

    /**
     * <p>
     * Turns on optimizer tracing. May take optional parameters:
     * </p>
     *
     * <ul>
     * <li>xml - If the first arg is the "xml" literal, then trace output will be
     * formatted as xml.</li>
     * <li>custom, $class - If the first arg is the "custom" literal, then the next arg must be
     * the name of a class which implements org.apache.derby.iapi.sql.compile.OptTrace
     * and which has a 0-arg constructor. The 0-arg constructor is called and the resulting
     * OptTrace object is plugged in to trace the optimizer.</li>
     * </ul>
     */
    public  void    loadTool( String... configurationParameters )
        throws SQLException
    {
        OptTrace    tracer;

         
         
    }
    private SQLException    cantInstantiate( String className )
    {
        return null;
    }

    /**
     * <p>
     * Print the optimizer trace and turn off tracing. Takes optional parameters:
     * </p>
     *
     * <ul>
     * <li><b>fileName</b> - Where to write the optimizer trace. If omitted, the trace is written to System.out.</li>
     * </ul>
     */
    public  void    unloadTool( final String... configurationParameters )
        throws SQLException
    {
        
    }

    ////////////////////////////////////////////////////////////////////////
    //
    //	MINIONS
    //
    ////////////////////////////////////////////////////////////////////////

    /** Wrap an exception in a SQLException */
    private SQLException    wrap( Throwable t )
    {
        return new SQLException( t.getMessage(), t );
    }
    
    private SQLException    wrap( String errorMessage )
    {
        String  sqlState = org.apache.derby.shared.common.reference.SQLState.JAVA_EXCEPTION.substring( 0, 5 );

        return new SQLException( errorMessage, sqlState );
    }
}

