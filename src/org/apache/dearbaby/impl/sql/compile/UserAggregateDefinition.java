/*

   Derby - Class org.apache.derby.impl.sql.compile.UserAggregateDefinition

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

package org.apache.dearbaby.impl.sql.compile;

import org.apache.derby.catalog.types.AggregateAliasInfo;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.ClassName;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.services.context.ContextService; 
import org.apache.derby.iapi.sql.compile.CompilerContext;
import org.apache.derby.iapi.sql.compile.TypeCompilerFactory;
import org.apache.derby.iapi.sql.dictionary.AliasDescriptor;
import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.iapi.types.JSQLType;

/**
 * Definition for user-defined aggregates.
 *
 */
class UserAggregateDefinition implements AggregateDefinition
{
    ///////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ///////////////////////////////////////////////////////////////////////////////////

    // the Aggregator interface has 3 parameter types
    private static  final   int INPUT_TYPE = 0;
    private static  final   int RETURN_TYPE = INPUT_TYPE + 1;
    private static  final   int AGGREGATOR_TYPE = RETURN_TYPE + 1;
    private static  final   int AGGREGATOR_PARAM_COUNT = AGGREGATOR_TYPE + 1;

    private static  final   String  DERBY_BYTE_ARRAY_NAME = "byte[]";

    ///////////////////////////////////////////////////////////////////////////////////
    //
    // STATE
    //
    ///////////////////////////////////////////////////////////////////////////////////

    private AliasDescriptor _alias;

    ///////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ///////////////////////////////////////////////////////////////////////////////////

	/**
	 * Conjure out of thin air.
	 */
    UserAggregateDefinition( AliasDescriptor alias )
    {
        _alias = alias;
    }

    ///////////////////////////////////////////////////////////////////////////////////
    //
    // BEHAVIOR
    //
    ///////////////////////////////////////////////////////////////////////////////////

    /** Get the wrapped alias descriptor */
    public  AliasDescriptor getAliasDescriptor() { return _alias; }

	/**
	 * Determines the result datatype and verifies that the input datatype is correct.
	 *
	 * @param inputType	the input type
	 * @param aggregatorClass (Output arg) the name of the Derby execution-time class which wraps the aggregate logic
	 *
	 * @return the result type of the user-defined aggregator
	 */
	public final DataTypeDescriptor	getAggregator
        ( DataTypeDescriptor inputType, StringBuffer aggregatorClass )
        throws StandardException
	{
		return null;
	}

    /**
     * Verify that an actual type is compatible with the expected type.
     */
    private void    vetCompatibility( Class<?> actualClass, Class<?> expectedClass, String sqlState )
        throws StandardException
    {
        if ( !actualClass.isAssignableFrom( expectedClass ) )
        {
            throw StandardException.newException
                (
                 sqlState,
                 _alias.getSchemaName(),
                 _alias.getName(),
                 expectedClass.toString(),
                 actualClass.toString()
                 );
        }
    }

	/**
	 * Wrap the input operand in an implicit CAST node as necessary in order to
     * coerce it the correct type for the aggregator. Return null if no cast is necessary.
	 */
    final ValueNode castInputValue
        ( ValueNode inputValue, ContextManager cm )
        throws StandardException
	{
        AggregateAliasInfo  aai = (AggregateAliasInfo) _alias.getAliasInfo();
        DataTypeDescriptor  expectedInputType = DataTypeDescriptor.getType( aai.getForType() );
        DataTypeDescriptor  actualInputType = inputValue.getTypeServices();

        // no cast needed if the types match exactly
        if ( expectedInputType.isExactTypeAndLengthMatch( actualInputType ) ) { return null; }
        else
        {
            return StaticMethodCallNode.makeCast(
                inputValue, expectedInputType, cm);
        }
    }
    
   

    /**
     * Make a "Could not instantiate aggregator" exception.
     */
    private StandardException   aggregatorInstantiation( Throwable t )
    {
        return StandardException.newException
            (
             SQLState.LANG_UDA_INSTANTIATION,
             t,
             _alias.getJavaClassName(),
             _alias.getSchemaName(),
             _alias.getName(),
             t.getMessage()
             );
    }

    
}
