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
import org.apache.derby.iapi.services.property.PropertyUtil;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.dictionary.ColumnDescriptor;
import org.apache.derby.iapi.sql.dictionary.SchemaDescriptor;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.sql.execute.ConstantAction;
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
    public ConstantAction makeConstantAction() throws StandardException
	{
		SchemaDescriptor		sd = getSchemaDescriptor();

		int columnCount = columnNames.length;
		int approxLength = 0;

		// bump the page size for the index,
		// if the approximate sizes of the columns in the key are
		// greater than the bump threshold.
		// Ideally, we would want to have atleast 2 or 3 keys fit in one page
		// With fix for beetle 5728, indexes on long types is not allowed
		// so we do not have to consider key columns of long types
		for (int i = 0; i < columnCount; i++)
		{
			ColumnDescriptor columnDescriptor = td.getColumnDescriptor(columnNames[i]);
			DataTypeDescriptor dts = columnDescriptor.getType();
			approxLength += dts.getTypeId().getApproximateLengthInBytes(dts);
		}


        if (approxLength > Property.IDX_PAGE_SIZE_BUMP_THRESHOLD)
        {

            if (((properties == null) ||
                 (properties.get(Property.PAGE_SIZE_PARAMETER) == null)) &&
                (PropertyUtil.getServiceProperty(
                     getLanguageConnectionContext().getTransactionCompile(),
                     Property.PAGE_SIZE_PARAMETER) == null))
            {
                // do not override the user's choice of page size, whether it
                // is set for the whole database or just set on this statement.

                if (properties == null)
                    properties = new Properties();

                properties.put(
                    Property.PAGE_SIZE_PARAMETER,
                    Property.PAGE_SIZE_DEFAULT_LONG);

            }
        }


		return getGenericConstantActionFactory().getCreateIndexConstantAction(
                    false, // not for CREATE TABLE
                    unique,
                    false, // it's not a UniqueWithDuplicateNulls Index
                    false, // it's not a constraint, so its checking
                           // is not deferrable
                    false, // initiallyDeferred: N/A
                    -1,    // constraintType: N/A
                    indexType,
                    sd.getSchemaName(),
                    indexName.getTableName(),
                    tableName.getTableName(),
                    td.getUUID(),
                    columnNames,
                    isAscending,
                    false,
                    null,
                    properties);
	}

	 

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
