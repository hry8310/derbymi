/*

   Derby - Class org.apache.derby.impl.sql.compile.CreateIndexNode

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
import java.util.Properties;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.Property;
import org.apache.derby.iapi.services.context.ContextManager; 
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.dictionary.ColumnDescriptor;
import org.apache.derby.iapi.sql.dictionary.SchemaDescriptor;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor; 
import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A CreateIndexNode is the root of a QueryTree that represents a CREATE INDEX
 * statement.
 *
 */

class CreateIndexNode extends DDLStatementNode
{
    private boolean             unique;
    private Properties          properties;
    private String              indexType;
    private TableName           indexName;
    private TableName           tableName; 
    private String[]            columnNames;
    private boolean[]           isAscending; 
    private TableDescriptor     td;

	/**
     * Constructor for a CreateIndexNode
	 *
	 * @param unique	True means it's a unique index
	 * @param indexType	The type of index
	 * @param indexName	The name of the index
	 * @param tableName	The name of the table the index will be on
	 * @param columnNameList	A list of column names, in the order they
	 *							appear in the index.
	 * @param properties	The optional properties list associated with the index.
     * @param cm Context manager
	 *
	 * @exception StandardException		Thrown on error
	 */
    CreateIndexNode(boolean unique,
                    String indexType,
                    TableName indexName,
                    TableName tableName,
                    List<String> columnNameList,
                    Properties properties,
                    ContextManager cm) throws StandardException
	{
        super(indexName, cm);
        this.unique = unique;
        this.indexType = indexType;
        this.indexName = indexName;
        this.tableName = tableName; 
        this.properties = properties;
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
				"unique: " + unique + "\n" +
				"indexType: " + indexType + "\n" +
				"indexName: " + indexName + "\n" +
				"tableName: " + tableName + "\n" +
				"properties: " + properties + "\n";
		}
		else
		{
			return "";
		}
	}

    String statementToString()
	{
		return "CREATE INDEX";
	}

	// We inherit the generate() method from DDLStatementNode.

	 
	/**
	 * Return true if the node references SESSION schema tables (temporary or permanent)
	 *
	 * @return	true if references SESSION schema tables, else false
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
	public boolean referencesSessionSchema()
		throws StandardException
	{
		//If create index is on a SESSION schema table, then return true.
		return isSessionSchema(td.getSchemaName());
	}

	/**
	 * Create the Constant information that will drive the guts of Execution.
	 *
	 * @exception StandardException		Thrown on failure
	 */
    
	 

    @Override
    void acceptChildren(Visitor v) throws StandardException {
        super.acceptChildren(v);

        if (indexName != null) {
            indexName = (TableName) indexName.accept(v);
        }

        if (tableName != null) {
            tableName = (TableName) tableName.accept(v);
        }
    }
}
