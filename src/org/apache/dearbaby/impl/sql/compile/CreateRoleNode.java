/*

   Derby - Class org.apache.derby.impl.sql.compile.CreateRoleNode

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

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.execute.ConstantAction;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A CreateRoleNode is the root of a QueryTree that
 * represents a CREATE ROLE statement.
 *
 */

class CreateRoleNode extends DDLStatementNode
{
    private String name;

    /**
     * Constructor for a CreateRoleNode
     *
     * @param roleName  The name of the new role
     *
     * @exception StandardException         Thrown on error
     */
    CreateRoleNode(String roleName, ContextManager cm) throws StandardException
    {
        super(null, cm);
        this.name = roleName;
    }

    /**
     * Convert this object to a String.  See comments in QueryTreeNode.java
     * for how this should be done for tree printing.
     *
     * @return  This object as a String
     */
    @Override
    public String toString()
    {
        if (SanityManager.DEBUG) {
            return super.toString() +
                "roleName: " + "\n" + name + "\n";
        } else {
            return "";
        }
    }
 
    public String statementToString()
    {
        return "CREATE ROLE";
    }

    // We inherit the generate() method from DDLStatementNode.

    /**
     * Create the Constant information that will drive the guts of Execution.
     *
     * @exception StandardException         Thrown on failure
     */
    @Override
    public ConstantAction   makeConstantAction()
    {
        return  getGenericConstantActionFactory().
                getCreateRoleConstantAction(name);
    }
}
