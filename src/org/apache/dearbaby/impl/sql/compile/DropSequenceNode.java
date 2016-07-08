/*

   Derby - Class org.apache.derby.impl.sql.compile.DropSequenceNode

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
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.execute.ConstantAction;

/**
 * A DropSequenceNode  represents a DROP SEQUENCE statement.
 */

class DropSequenceNode extends DDLStatementNode {
    private TableName dropItem;

    /**
     * Constructor for a DropSequenceNode
     *
     * @param dropSequenceName The name of the sequence being dropped
     * @param cm               The context manager
     * @throws StandardException
     */
    DropSequenceNode(TableName dropSequenceName, ContextManager cm) {
        super(dropSequenceName, cm);
        dropItem = dropSequenceName;
    }

    public String statementToString() {
        return "DROP ".concat(dropItem.getTableName());
    }

     

    // inherit generate() method from DDLStatementNode


    /**
     * Create the Constant information that will drive the guts of Execution.
     *
     * @throws StandardException Thrown on failure
     */
    @Override
    public ConstantAction makeConstantAction() throws StandardException {
        return getGenericConstantActionFactory().getDropSequenceConstantAction(getSchemaDescriptor(), getRelativeName());
	}

    @Override
    void acceptChildren(Visitor v) throws StandardException {
        super.acceptChildren(v);

        if (dropItem != null) {
            dropItem = (TableName) dropItem.accept(v);
        }
    }
}
