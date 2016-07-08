/*

   Derby - Class org.apache.derby.impl.sql.compile.SetSchemaNode

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

package	org.apache.dearbaby.impl.sql.compile;

import java.util.List;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.StatementType;
import org.apache.derby.iapi.sql.execute.ConstantAction;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A SetSchemaNode is the root of a QueryTree that 
 * represents a SET SCHEMA statement.  It isn't
 * replicated, but it generates a ConstantAction
 * because it is basically easier than generating
 * the code from scratch.
 *
 */

class SetSchemaNode extends MiscellaneousStatementNode
{
	private String 	name;
	private int 	type;
	
	/**
     * Constructor for a SetSchemaNode
	 *
	 * @param schemaName	The name of the new schema
	 * @param type			Type of schema name could be USER or dynamic parameter
     * @param cm            The context manager
	 *
	 */
    SetSchemaNode(
            String schemaName,
            int type,
            ContextManager cm) {
        super(cm);
        this.name = schemaName;
        this.type = type;
	}

    /**
	 * Convert this object to a String.  See comments in QueryTreeNode.java
	 * for how this should be done for tree printing.
	 *
	 * @return	This object as a String
	 */
    @Override
	public String toString()
	{
		if (SanityManager.DEBUG)
		{
			return super.toString() + 
				(type == StatementType.SET_SCHEMA_USER ? "schemaName: \nUSER\n" :
				(type == StatementType.SET_SCHEMA_DYNAMIC ? "schemaName: \n?\n" : 
					"schemaName: " + "\n" + name + "\n"));
		}
		else
		{
			return "";
		}
	}

    String statementToString()
	{
		return "SET SCHEMA";
	}

	/**
	 * Create the Constant information that will drive the guts of Execution.
	 *
	 * @exception StandardException		Thrown on failure
	 */
    @Override
    public ConstantAction makeConstantAction() throws StandardException
	{
		return	getGenericConstantActionFactory().getSetSchemaConstantAction(name, type);		
	}
	 
	/**
	 * Generate the code to create the ParameterValueSet, if necessary,
	 * when constructing the activation.  Also generate the code to call
	 * a method that will throw an exception if we try to execute without
	 * all the parameters being set.
	 * 
	 * @param acb	The ActivationClassBuilder for the class we're building
	 */

	void generateParameterValueSet(ActivationClassBuilder acb)
		throws StandardException
	{
        List<ParameterNode>
            parameterList = getCompilerContext().getParameterList();
		// parameter list size should be 1
		if (SanityManager.DEBUG)
			SanityManager.ASSERT(parameterList != null && parameterList.size() == 1);
			
		ParameterNode.generateParameterValueSet ( acb, 1, parameterList);
	}

	/**
	 * Returns the type of activation this class
	 * generates.
	 * 
	 * @return  NEED_PARAM_ACTIVATION or
	 *			NEED_NOTHING_ACTIVATION depending on params
	 *
	 */
    @Override
	int activationKind()
	{
		/*
		** We need parameters 
		** only for those that have parameters.
		*/
		if (type == StatementType.SET_SCHEMA_DYNAMIC)
		{
			return StatementNode.NEED_PARAM_ACTIVATION;
		}
		else
		{
			return StatementNode.NEED_NOTHING_ACTIVATION;
		}
	}
}
